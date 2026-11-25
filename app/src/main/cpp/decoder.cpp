// SPDX-License-Identifier: GPL-3.0-or-later
#include "decoder.h"

#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "slowdown", __VA_ARGS__)

namespace slowdown {

namespace {

constexpr int ENCODING_16 = 2, ENCODING_8 = 3, ENCODING_FLOAT = 4, ENCODING_24 = 21, ENCODING_32 = 22;
constexpr int64_t TIMEOUT_US = 10000;

}  // namespace

Decoder::~Decoder() {
    cancel_ = true;
    if (thread_.joinable()) thread_.join();
    if (codec_) {
        AMediaCodec_stop(codec_);
        AMediaCodec_delete(codec_);
    }
    if (extractor_) AMediaExtractor_delete(extractor_);
    if (fd_ >= 0) ::close(fd_);
}

std::unique_ptr<Decoder> Decoder::open(int fd, int64_t offset, int64_t length, std::string& error) {
    std::unique_ptr<Decoder> d(new Decoder());
    d->fd_ = ::dup(fd);
    if (length < 0) {
        struct stat st{};
        length = fstat(d->fd_, &st) == 0 ? int64_t(st.st_size) - offset : 0;
    }
    d->extractor_ = AMediaExtractor_new();
    if (AMediaExtractor_setDataSourceFd(d->extractor_, d->fd_, offset, length) != AMEDIA_OK) {
        error = "unreadable";
        return nullptr;
    }
    // La primera pista de audio (en un video, su sonido)
    size_t tracks = AMediaExtractor_getTrackCount(d->extractor_);
    for (size_t i = 0; i < tracks && !d->codec_; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(d->extractor_, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime) && std::strncmp(mime, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(d->extractor_, i);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &d->expected_us_);
            d->codec_ = AMediaCodec_createDecoderByType(mime);
            if (d->codec_) {
                // Muestras en coma flotante, si el códec las da; si no, sale en 16 bits
                AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_PCM_ENCODING, ENCODING_FLOAT);
                if (AMediaCodec_configure(d->codec_, format, nullptr, nullptr, 0) != AMEDIA_OK ||
                    AMediaCodec_start(d->codec_) != AMEDIA_OK) {
                    AMediaCodec_delete(d->codec_);
                    d->codec_ = nullptr;
                    error = std::string("codec: ") + mime;
                }
            } else {
                error = std::string("codec: ") + mime;
            }
        }
        AMediaFormat_delete(format);
    }
    if (!d->codec_) {
        if (error.empty()) error = "no audio";
        return nullptr;
    }
    // Hasta saber qué sale del códec
    bool more = true;
    while (!d->audio_ && more) more = d->step(error);
    if (!d->audio_) {
        if (error.empty()) error = "no audio";
        return nullptr;
    }
    Decoder* self = d.get();
    d->thread_ = std::thread([self, more] {
        std::string failure;
        bool going = more;
        while (going && !self->cancel_) going = self->step(failure);
        if (!failure.empty()) LOG("decoding: %s", failure.c_str());
        self->audio_->finish(!failure.empty() && !self->cancel_);
    });
    return d;
}

void Decoder::output_format() {
    AMediaFormat* format = AMediaCodec_getOutputFormat(codec_);
    int32_t rate = 44100, channels = 2, encoding = ENCODING_16;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
    if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_PCM_ENCODING, &encoding)) encoding = ENCODING_16;
    AMediaFormat_delete(format);
    encoding_ = encoding;
    in_channels_ = std::max(1, channels);
    if (!audio_) {
        int out_channels = std::min(in_channels_, 2);
        audio_ = std::make_shared<Audio>(rate, out_channels, expected_us_ * rate / 1000000);
        LOG("decoding: %d Hz, %d channels, encoding %d", rate, channels, encoding);
    }
}

bool Decoder::step(std::string& error) {
    if (!input_done_) {
        ssize_t index = AMediaCodec_dequeueInputBuffer(codec_, TIMEOUT_US);
        if (index >= 0) {
            size_t capacity = 0;
            uint8_t* buffer = AMediaCodec_getInputBuffer(codec_, size_t(index), &capacity);
            ssize_t size = AMediaExtractor_readSampleData(extractor_, buffer, capacity);
            if (size < 0) {
                AMediaCodec_queueInputBuffer(codec_, size_t(index), 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                input_done_ = true;
            } else {
                int64_t time = AMediaExtractor_getSampleTime(extractor_);
                AMediaCodec_queueInputBuffer(codec_, size_t(index), 0, size_t(size), uint64_t(std::max<int64_t>(0, time)), 0);
                AMediaExtractor_advance(extractor_);
            }
        }
    }
    AMediaCodecBufferInfo info{};
    ssize_t index = AMediaCodec_dequeueOutputBuffer(codec_, &info, TIMEOUT_US);
    if (index >= 0) {
        if (!audio_) output_format();
        if (info.size > 0) {
            size_t capacity = 0;
            uint8_t* buffer = AMediaCodec_getOutputBuffer(codec_, size_t(index), &capacity);
            if (buffer) deliver(buffer + info.offset, size_t(info.size));
        }
        AMediaCodec_releaseOutputBuffer(codec_, size_t(index), false);
        if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) return false;
    } else if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        output_format();
    } else if (index != AMEDIACODEC_INFO_TRY_AGAIN_LATER && index != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
        error = "codec error " + std::to_string(index);
        return false;
    }
    return true;
}

void Decoder::deliver(const uint8_t* data, size_t size) {
    const int in = in_channels_;
    int bytes = encoding_ == ENCODING_FLOAT || encoding_ == ENCODING_32 ? 4
              : encoding_ == ENCODING_24 ? 3 : encoding_ == ENCODING_8 ? 1 : 2;
    size_t frames = size / size_t(bytes * in);
    std::vector<float> samples(frames * size_t(in));
    for (size_t i = 0; i < frames * size_t(in); ++i) {
        const uint8_t* p = data + i * size_t(bytes);
        float v;
        switch (encoding_) {
            case ENCODING_FLOAT: std::memcpy(&v, p, 4); break;
            case ENCODING_32: { int32_t s; std::memcpy(&s, p, 4); v = float(s) / 2147483648.0f; break; }
            case ENCODING_24: { int32_t s = int32_t(uint32_t(p[0]) << 8 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 24); v = float(s) / 2147483648.0f; break; }
            case ENCODING_8: v = (float(p[0]) - 128.0f) / 128.0f; break;
            default: { int16_t s; std::memcpy(&s, p, 2); v = float(s) / 32768.0f; break; }
        }
        samples[i] = v;
    }
    if (in <= 2) {
        audio_->append(samples.data(), int64_t(frames));
        return;
    }
    // Más de dos canales, en el orden de Android (FL FR FC LFE BL BR SL SR…): una mezcla estéreo, sin el LFE
    scratch_.resize(frames * 2);
    const float side = 0.7071f;
    for (size_t f = 0; f < frames; ++f) {
        const float* s = samples.data() + f * size_t(in);
        float l = s[0], r = s[1];
        float norm = 1.0f;
        if (in > 2) { l += side * s[2]; r += side * s[2]; norm += side; }
        for (int c = 4; c < in; ++c) {
            if (c % 2 == 0) l += side * s[c]; else r += side * s[c];
        }
        if (in > 4) norm += side * float((in - 4 + 1) / 2);
        scratch_[2 * f] = l / norm;
        scratch_[2 * f + 1] = r / norm;
    }
    audio_->append(scratch_.data(), int64_t(frames));
}

}  // namespace slowdown
