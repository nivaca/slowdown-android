// SPDX-License-Identifier: GPL-3.0-or-later
// La decodificación con los códecs de Android (MediaExtractor y MediaCodec del NDK), para lo que no es MP3,
// FLAC ni WAV: AAC, Opus, Vorbis, el sonido de un video…
//
// open_codec() decodifica hasta conocer el formato de lo que sale del códec (la
// frecuencia y los canales verdaderos: un AAC HE anuncia a veces la mitad).
#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <unistd.h>

#include <cstring>

#include "decoder.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "slowdown", __VA_ARGS__)

namespace slowdown {

namespace {

constexpr int ENCODING_16 = 2, ENCODING_8 = 3, ENCODING_FLOAT = 4, ENCODING_24 = 21, ENCODING_32 = 22;
constexpr int64_t TIMEOUT_US = 1000;

class CodecDecoder : public Decoder {
public:
    int fd = -1;
    AMediaExtractor* extractor = nullptr;
    AMediaCodec* codec = nullptr;
    bool input_done = false;
    int64_t expected_us = 0;
    // Lo que sale del códec. No se le piden muestras en coma flotante: el de FLAC de este teléfono decía darlas
    // y daba 16 bits.
    int encoding = ENCODING_16;
    int in_channels = 2;
    std::vector<float> samples;

    ~CodecDecoder() override {
        stop();
        if (codec) {
            AMediaCodec_stop(codec);
            AMediaCodec_delete(codec);
        }
        if (extractor) AMediaExtractor_delete(extractor);
        if (fd >= 0) ::close(fd);
    }
    const char* kind() const override { return "codec"; }

    // Hasta saber qué sale del códec; luego, el hilo
    bool prepare(std::string& error) {
        bool more = true;
        while (!audio_ && more) more = step(error);
        if (!audio_) {
            if (error.empty()) error = "no audio";
            return false;
        }
        start(more);
        return true;
    }

    void output_format() {
        AMediaFormat* format = AMediaCodec_getOutputFormat(codec);
        int32_t rate = 44100, channels = 2, pcm = ENCODING_16;
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, &rate);
        AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, &channels);
        if (!AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_PCM_ENCODING, &pcm)) pcm = ENCODING_16;
        AMediaFormat_delete(format);
        encoding = pcm;
        in_channels = std::max(1, channels);
        if (!audio_) {
            audio_ = std::make_shared<Audio>(rate, std::min(in_channels, 2), expected_us * rate / 1000000);
            LOG("codec: %d Hz, %d channels, encoding %d", rate, channels, pcm);
        }
    }

    void deliver(const uint8_t* data, size_t size) {
        const int in = in_channels;
        int bytes = encoding == ENCODING_FLOAT || encoding == ENCODING_32 ? 4
                  : encoding == ENCODING_24 ? 3 : encoding == ENCODING_8 ? 1 : 2;
        size_t frames = size / size_t(bytes * in);
        samples.resize(frames * size_t(in));
        for (size_t i = 0; i < frames * size_t(in); ++i) {
            const uint8_t* p = data + i * size_t(bytes);
            float v;
            switch (encoding) {
                case ENCODING_FLOAT: std::memcpy(&v, p, 4); break;
                case ENCODING_32: { int32_t s; std::memcpy(&s, p, 4); v = float(s) / 2147483648.0f; break; }
                case ENCODING_24: {
                    int32_t s = int32_t(uint32_t(p[0]) << 8 | uint32_t(p[1]) << 16 | uint32_t(p[2]) << 24);
                    v = float(s) / 2147483648.0f;
                    break;
                }
                case ENCODING_8: v = (float(p[0]) - 128.0f) / 128.0f; break;
                default: { int16_t s; std::memcpy(&s, p, 2); v = float(s) / 32768.0f; break; }
            }
            samples[i] = v;
        }
        append(samples.data(), frames, in);
    }

    bool step(std::string& error) override {
        // Todo lo que el códec acepte, sin esperar; luego todo lo que tenga listo, con una espera corta si no
        // tiene nada.
        while (!input_done) {
            ssize_t index = AMediaCodec_dequeueInputBuffer(codec, 0);
            if (index < 0) break;
            size_t capacity = 0;
            uint8_t* buffer = AMediaCodec_getInputBuffer(codec, size_t(index), &capacity);
            ssize_t size = AMediaExtractor_readSampleData(extractor, buffer, capacity);
            if (size < 0) {
                AMediaCodec_queueInputBuffer(codec, size_t(index), 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                input_done = true;
            } else {
                int64_t time = AMediaExtractor_getSampleTime(extractor);
                AMediaCodec_queueInputBuffer(codec, size_t(index), 0, size_t(size), uint64_t(std::max<int64_t>(0, time)), 0);
                AMediaExtractor_advance(extractor);
            }
        }
        int64_t wait = TIMEOUT_US;
        while (true) {
            AMediaCodecBufferInfo info{};
            ssize_t index = AMediaCodec_dequeueOutputBuffer(codec, &info, wait);
            if (index >= 0) {
                wait = 0;
                if (!audio_) output_format();
                if (info.size > 0) {
                    size_t capacity = 0;
                    uint8_t* buffer = AMediaCodec_getOutputBuffer(codec, size_t(index), &capacity);
                    if (buffer) deliver(buffer + info.offset, size_t(info.size));
                }
                AMediaCodec_releaseOutputBuffer(codec, size_t(index), false);
                if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) return false;
            } else if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                output_format();
            } else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                return true;
            } else if (index != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                error = "codec error " + std::to_string(index);
                return false;
            }
        }
    }
};

}  // namespace

std::unique_ptr<Decoder> open_codec(int fd, int64_t offset, int64_t length, std::string& error) {
    auto d = std::make_unique<CodecDecoder>();
    d->fd = ::dup(fd);
    d->extractor = AMediaExtractor_new();
    if (AMediaExtractor_setDataSourceFd(d->extractor, d->fd, offset, length) != AMEDIA_OK) {
        error = "unreadable";
        return nullptr;
    }
    // La primera pista de audio (en un video, su sonido)
    size_t tracks = AMediaExtractor_getTrackCount(d->extractor);
    for (size_t i = 0; i < tracks && !d->codec; ++i) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(d->extractor, i);
        const char* mime = nullptr;
        if (AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime) && std::strncmp(mime, "audio/", 6) == 0) {
            AMediaExtractor_selectTrack(d->extractor, i);
            AMediaFormat_getInt64(format, AMEDIAFORMAT_KEY_DURATION, &d->expected_us);
            d->codec = AMediaCodec_createDecoderByType(mime);
            if (d->codec && (AMediaCodec_configure(d->codec, format, nullptr, nullptr, 0) != AMEDIA_OK ||
                             AMediaCodec_start(d->codec) != AMEDIA_OK)) {
                AMediaCodec_delete(d->codec);
                d->codec = nullptr;
            }
            if (!d->codec) error = std::string("codec: ") + mime;
        }
        AMediaFormat_delete(format);
    }
    if (!d->codec) {
        if (error.empty()) error = "no audio";
        return nullptr;
    }
    if (!d->prepare(error)) return nullptr;
    return d;
}

}  // namespace slowdown
