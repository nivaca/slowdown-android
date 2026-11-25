// SPDX-License-Identifier: GPL-3.0-or-later
#include "exporter.h"

#include <unistd.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

#include "engine.h"
#include "hearing.h"

#ifdef __ANDROID__
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaMuxer.h>
#endif

namespace slowdown {

namespace {

// Lo que recibe el audio ya hecho, en muestras de 16 bits intercaladas
class Sink {
public:
    virtual ~Sink() = default;
    virtual bool write(const int16_t* samples, size_t frames, std::string& error) = 0;
    virtual bool finish(std::string& error) = 0;
};

// WAV: una cabecera que se completa al final (por eso el archivo debe poder volver atrás)
class WavSink : public Sink {
public:
    WavSink(int fd, int rate, int channels) : fd_(fd), rate_(rate), channels_(channels) {}

    bool start(std::string& error) {
        uint8_t header[44] = {};
        return put(header, sizeof header, error);
    }

    bool write(const int16_t* samples, size_t frames, std::string& error) override {
        size_t bytes = frames * size_t(channels_) * 2;
        data_ += bytes;
        return put(samples, bytes, error);
    }

    bool finish(std::string& error) override {
        uint8_t h[44];
        auto u32 = [&](int at, uint32_t v) { for (int i = 0; i < 4; ++i) h[at + i] = uint8_t(v >> (8 * i)); };
        auto u16 = [&](int at, uint16_t v) { h[at] = uint8_t(v); h[at + 1] = uint8_t(v >> 8); };
        std::memcpy(h, "RIFF", 4);
        u32(4, uint32_t(36 + data_));
        std::memcpy(h + 8, "WAVEfmt ", 8);
        u32(16, 16);
        u16(20, 1);  // PCM
        u16(22, uint16_t(channels_));
        u32(24, uint32_t(rate_));
        u32(28, uint32_t(rate_ * channels_ * 2));
        u16(32, uint16_t(channels_ * 2));
        u16(34, 16);
        std::memcpy(h + 36, "data", 4);
        u32(40, uint32_t(data_));
        if (::pwrite(fd_, h, sizeof h, 0) != ssize_t(sizeof h)) {
            error = "cannot write the header (the file cannot be rewound)";
            return false;
        }
        return true;
    }

private:
    int fd_, rate_, channels_;
    uint64_t data_ = 0;

    bool put(const void* bytes, size_t size, std::string& error) {
        const char* p = static_cast<const char*>(bytes);
        while (size) {
            ssize_t n = ::write(fd_, p, size);
            if (n <= 0) {
                error = "cannot write";
                return false;
            }
            p += n;
            size -= size_t(n);
        }
        return true;
    }
};

#ifdef __ANDROID__
// M4A: AAC con el codificador de Android, en un MP4 hecho por su MediaMuxer
class M4aSink : public Sink {
public:
    M4aSink(int fd, int rate, int channels) : rate_(rate), channels_(channels) {
        muxer_ = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
        codec_ = AMediaCodec_createEncoderByType("audio/mp4a-latm");
    }

    ~M4aSink() override {
        if (codec_) {
            AMediaCodec_stop(codec_);
            AMediaCodec_delete(codec_);
        }
        if (muxer_) AMediaMuxer_delete(muxer_);
    }

    bool start(std::string& error) {
        if (!muxer_ || !codec_) {
            error = "no AAC encoder";
            return false;
        }
        AMediaFormat* format = AMediaFormat_new();
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, rate_);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, channels_);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, channels_ * 96000);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_AAC_PROFILE, 2);  // AAC-LC
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, 65536);
        media_status_t status = AMediaCodec_configure(codec_, format, nullptr, nullptr,
                                                      AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        AMediaFormat_delete(format);
        if (status != AMEDIA_OK || AMediaCodec_start(codec_) != AMEDIA_OK) {
            error = "the AAC encoder does not start";
            return false;
        }
        return true;
    }

    bool write(const int16_t* samples, size_t frames, std::string& error) override {
        const uint8_t* bytes = reinterpret_cast<const uint8_t*>(samples);
        size_t left = frames * size_t(channels_) * 2;
        while (left) {
            ssize_t index = AMediaCodec_dequeueInputBuffer(codec_, 10000);
            if (index >= 0) {
                size_t capacity = 0;
                uint8_t* buffer = AMediaCodec_getInputBuffer(codec_, size_t(index), &capacity);
                size_t take = std::min(left, capacity - capacity % size_t(channels_ * 2));
                std::memcpy(buffer, bytes, take);
                int64_t time = written_ * 1000000 / rate_;
                AMediaCodec_queueInputBuffer(codec_, size_t(index), 0, take, uint64_t(time), 0);
                written_ += int64_t(take) / (channels_ * 2);
                bytes += take;
                left -= take;
            }
            if (!drain(false, error)) return false;
        }
        return true;
    }

    bool finish(std::string& error) override {
        for (int tries = 0; tries < 200; ++tries) {
            ssize_t index = AMediaCodec_dequeueInputBuffer(codec_, 10000);
            if (index >= 0) {
                AMediaCodec_queueInputBuffer(codec_, size_t(index), 0, 0, uint64_t(written_ * 1000000 / rate_),
                                             AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                break;
            }
            if (!drain(false, error)) return false;
        }
        if (!drain(true, error)) return false;
        if (started_ && AMediaMuxer_stop(muxer_) != AMEDIA_OK) {
            error = "the MP4 file cannot be finished";
            return false;
        }
        return true;
    }

private:
    int rate_, channels_;
    AMediaMuxer* muxer_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    ssize_t track_ = -1;
    bool started_ = false;
    int64_t written_ = 0;

    // Lo que tenga listo el codificador, al MP4; con END, hasta el final
    bool drain(bool end, std::string& error) {
        while (true) {
            AMediaCodecBufferInfo info{};
            ssize_t index = AMediaCodec_dequeueOutputBuffer(codec_, &info, end ? 10000 : 0);
            if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
                AMediaFormat* format = AMediaCodec_getOutputFormat(codec_);
                track_ = AMediaMuxer_addTrack(muxer_, format);
                AMediaFormat_delete(format);
                if (track_ < 0 || AMediaMuxer_start(muxer_) != AMEDIA_OK) {
                    error = "the MP4 file cannot be started";
                    return false;
                }
                started_ = true;
            } else if (index >= 0) {
                size_t capacity = 0;
                uint8_t* buffer = AMediaCodec_getOutputBuffer(codec_, size_t(index), &capacity);
                bool config = info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG;
                if (!config && info.size > 0 && started_) {
                    AMediaMuxer_writeSampleData(muxer_, size_t(track_), buffer, &info);
                }
                AMediaCodec_releaseOutputBuffer(codec_, size_t(index), false);
                if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) return true;
            } else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                if (!end) return true;
            } else if (index != AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED) {
                error = "AAC encoder error " + std::to_string(index);
                return false;
            }
        }
    }
};
#endif

}  // namespace

Export::Export(std::shared_ptr<Audio> audio, int fd, ExportSettings settings)
    : audio_(std::move(audio)), fd_(::dup(fd)), settings_(settings) {}

Export::~Export() {
    cancel_ = true;
    if (thread_.joinable()) thread_.join();
    if (fd_ >= 0) ::close(fd_);
}

std::unique_ptr<Export> start_export(std::shared_ptr<Audio> audio, int fd, ExportSettings settings) {
    auto job = std::make_unique<Export>(std::move(audio), fd, settings);
    Export* self = job.get();
    job->thread_ = std::thread([self] { self->run(); });
    return job;
}

void Export::run() {
    Audio& audio = *audio_;
    const int rate = audio.rate, ch = audio.channels;
    auto finish = [this](const std::string& error) {
        error_ = error;
        done_ = true;
    };
    if (!audio.done()) return finish("the file is still being read");
    int64_t first = int64_t(std::max(0.0, settings_.start) * rate);
    int64_t last = settings_.end < 0 ? audio.decoded() : std::min(audio.decoded(), int64_t(settings_.end * rate));
    if (last <= first) return finish("nothing to export");

    std::unique_ptr<Sink> sink;
    std::string error;
    if (settings_.format == EXPORT_WAV) {
        auto wav = std::make_unique<WavSink>(fd_, rate, ch);
        if (!wav->start(error)) return finish(error);
        sink = std::move(wav);
    } else {
#ifdef __ANDROID__
        auto m4a = std::make_unique<M4aSink>(fd_, rate, ch);
        if (!m4a->start(error)) return finish(error);
        sink = std::move(m4a);
#else
        return finish("M4A only on Android");
#endif
    }

    // Un Renderer propio, sin rampa al empezar, y la manera de oír
    Renderer renderer(audio_);
    renderer.set_quality(settings_.quality);
    renderer.set_speed(settings_.speed);
    renderer.set_pitch(settings_.pitch);
    renderer.set_formants(settings_.formants);
    renderer.seek(double(first), false);
    Hearing hearing(rate);
    hearing.set(settings_.channels, settings_.filter);
    // (un filtro retrasa el sonido medio núcleo: esos cuadros del principio se tiran, y al final se completan)
    int64_t delay = settings_.filter == NONE ? 0 : (int64_t(KERNEL_SECONDS * rate) | 1) / 2;
    int64_t skip = delay;
    std::vector<int16_t> pcm;
    bool seam = false;
    auto emit = [&](std::vector<float>& frames) -> bool {
        hearing.process(frames, ch);
        size_t n = frames.size() / size_t(ch);
        size_t from = size_t(std::min<int64_t>(skip, int64_t(n)));
        skip -= int64_t(from);
        pcm.resize((n - from) * size_t(ch));
        for (size_t i = 0; i < pcm.size(); ++i) {
            float v = frames[from * size_t(ch) + i] * float(settings_.gain);
            v = std::min(1.0f, std::max(-1.0f, v));
            pcm[i] = int16_t(std::lrint(v * 32767.0f));
        }
        return pcm.empty() || sink->write(pcm.data(), pcm.size() / size_t(ch), error);
    };
    int empty = 0;  // (bloques vacíos seguidos: con el archivo ya leído, solo al final del estirador)
    while (!cancel_) {
        std::optional<Block> block = renderer.render(4096, seam);
        if (!block || block->start >= double(last)) break;
        if (!block->size()) {
            if (++empty > 10000) break;
            continue;
        }
        empty = 0;
        // Lo que pasa del final se corta
        int64_t keep = block->step > 0 ? int64_t(std::ceil((double(last) - block->start) / block->step)) : block->size();
        if (keep < block->size()) block->frames.resize(size_t(keep) * size_t(ch));
        if (!emit(block->frames)) return finish(error);
        progress_ = std::min(1.0, (block->position() + double(block->size()) * block->step - double(first)) /
                                      double(last - first));
    }
    if (cancel_) return finish("cancelled");
    if (delay) {
        std::vector<float> silence(size_t(delay) * size_t(ch), 0.0f);
        if (!emit(silence)) return finish(error);
    }
    if (!sink->finish(error)) return finish(error);
    progress_ = 1.0;
    finish("");
}

}  // namespace slowdown
