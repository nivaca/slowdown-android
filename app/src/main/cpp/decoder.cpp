// SPDX-License-Identifier: GPL-3.0-or-later
#include "decoder.h"

#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <chrono>
#include <cstring>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "slowdown", __VA_ARGS__)
#else
#include <cstdio>
#define LOG(...) (std::fprintf(stderr, __VA_ARGS__), std::fputc('\n', stderr))
#endif

#define MINIMP3_IMPLEMENTATION
#define MINIMP3_FLOAT_OUTPUT
#include "minimp3_ex.h"

#define DR_FLAC_IMPLEMENTATION
#define DR_FLAC_NO_STDIO
#include "dr_flac.h"

#define DR_WAV_IMPLEMENTATION
#define DR_WAV_NO_STDIO
#include "dr_wav.h"

namespace slowdown {

namespace {
constexpr size_t STEP_FRAMES = 8192;  // cuadros por paso de los decodificadores propios
}

// --- File

size_t File::read(void* buffer, size_t bytes) {
    int64_t left = std::max<int64_t>(0, length - pos);
    size_t want = size_t(std::min<int64_t>(int64_t(bytes), left));
    size_t got = 0;
    while (got < want) {
        ssize_t n = ::pread(fd, static_cast<char*>(buffer) + got, want - got, off_t(offset + pos));
        if (n <= 0) break;
        got += size_t(n);
        pos += n;
    }
    return got;
}

bool File::seek(int64_t to) {
    if (to < 0 || to > length) return false;
    pos = to;
    return true;
}

File::~File() {
    if (fd >= 0) ::close(fd);
}

// --- Decoder

void Decoder::start(bool more) {
    thread_ = std::thread([this, more] {
        auto t0 = std::chrono::steady_clock::now();
        std::string failure;
        bool going = more;
        while (going && !cancel_) going = step(failure);
        if (!failure.empty()) LOG("decoding: %s", failure.c_str());
        double took = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
        LOG("%s: decoded %.1f s of audio in %.2f s", kind(), double(audio_->decoded()) / audio_->rate, took);
        audio_->finish(!failure.empty() && !cancel_);
    });
}

void Decoder::stop() {
    cancel_ = true;
    if (thread_.joinable()) thread_.join();
}

void Decoder::append(const float* samples, size_t frames, int in) {
    if (in <= 2) {
        audio_->append(samples, int64_t(frames));
        return;
    }
    // Más de dos canales, en el orden habitual (FL FR FC LFE BL BR SL SR…): una mezcla estéreo, sin el LFE
    mixed_.resize(frames * 2);
    const float side = 0.7071f;
    for (size_t f = 0; f < frames; ++f) {
        const float* s = samples + f * size_t(in);
        float l = s[0] + side * s[2], r = s[1] + side * s[2];
        float norm = 1.0f + side;
        for (int c = 4; c < in; ++c) {
            if (c % 2 == 0) l += side * s[c];
            else r += side * s[c];
        }
        if (in > 4) norm += side * float((in - 4 + 1) / 2);
        mixed_[2 * f] = l / norm;
        mixed_[2 * f + 1] = r / norm;
    }
    audio_->append(mixed_.data(), int64_t(frames));
}

namespace {

// --- MP3, con minimp3 (que quita el retraso y el relleno del codificador, como FFmpeg en py-slowdown)

class Mp3Decoder : public Decoder {
public:
    File file;
    mp3dec_io_t io{};
    mp3dec_ex_t mp3{};
    bool opened = false;
    std::vector<float> buffer;

    ~Mp3Decoder() override {
        stop();
        if (opened) mp3dec_ex_close(&mp3);
    }
    const char* kind() const override { return "mp3"; }

    bool open(std::string& error) {
        io.read = [](void* buf, size_t size, void* self) { return static_cast<File*>(self)->read(buf, size); };
        io.read_data = &file;
        io.seek = [](uint64_t position, void* self) { return static_cast<File*>(self)->seek(int64_t(position)) ? 0 : -1; };
        io.seek_data = &file;
        // MP3D_SEEK_TO_SAMPLE: recorre antes las cabeceras de los cuadros, y así sabe cuántas muestras son
        if (mp3dec_ex_open_cb(&mp3, &io, MP3D_SEEK_TO_SAMPLE) != 0 || mp3.info.hz <= 0 || mp3.info.channels <= 0) {
            error = "mp3";
            return false;
        }
        opened = true;
        int channels = mp3.info.channels;
        audio_ = std::make_shared<Audio>(mp3.info.hz, std::min(channels, 2), int64_t(mp3.samples / uint64_t(channels)));
        return true;
    }

    bool step(std::string& error) override {
        int channels = mp3.info.channels;
        buffer.resize(STEP_FRAMES * size_t(channels));
        size_t got = mp3dec_ex_read(&mp3, buffer.data(), buffer.size());
        if (got == 0) {
            if (mp3.last_error) error = "mp3 error " + std::to_string(mp3.last_error);
            return false;
        }
        append(buffer.data(), got / size_t(channels), channels);
        return true;
    }
};

// --- FLAC, con dr_flac

class FlacDecoder : public Decoder {
public:
    File file;
    drflac* flac = nullptr;
    std::vector<float> buffer;

    ~FlacDecoder() override {
        stop();
        if (flac) drflac_close(flac);
    }
    const char* kind() const override { return "flac"; }

    static size_t on_read(void* self, void* out, size_t bytes) { return static_cast<File*>(self)->read(out, bytes); }
    static drflac_bool32 on_seek(void* self, int offset, drflac_seek_origin origin) {
        File* f = static_cast<File*>(self);
        int64_t base = origin == DRFLAC_SEEK_SET ? 0 : origin == DRFLAC_SEEK_CUR ? f->pos : f->length;
        return f->seek(base + offset);
    }
    static drflac_bool32 on_tell(void* self, drflac_int64* cursor) {
        *cursor = static_cast<File*>(self)->pos;
        return DRFLAC_TRUE;
    }

    bool open(std::string& error) {
        flac = drflac_open(on_read, on_seek, on_tell, &file, nullptr);
        if (!flac || flac->channels == 0 || flac->sampleRate == 0) {
            error = "flac";
            return false;
        }
        audio_ = std::make_shared<Audio>(int(flac->sampleRate), std::min<int>(flac->channels, 2),
                                         int64_t(flac->totalPCMFrameCount));
        return true;
    }

    bool step(std::string&) override {
        buffer.resize(STEP_FRAMES * flac->channels);
        drflac_uint64 got = drflac_read_pcm_frames_f32(flac, STEP_FRAMES, buffer.data());
        if (got == 0) return false;
        append(buffer.data(), size_t(got), flac->channels);
        return true;
    }
};

// --- WAV (también RF64 y AIFF), con dr_wav

class WavDecoder : public Decoder {
public:
    File file;
    drwav wav{};
    bool opened = false;
    std::vector<float> buffer;

    ~WavDecoder() override {
        stop();
        if (opened) drwav_uninit(&wav);
    }
    const char* kind() const override { return "wav"; }

    static size_t on_read(void* self, void* out, size_t bytes) { return static_cast<File*>(self)->read(out, bytes); }
    static drwav_bool32 on_seek(void* self, int offset, drwav_seek_origin origin) {
        File* f = static_cast<File*>(self);
        int64_t base = origin == DRWAV_SEEK_SET ? 0 : origin == DRWAV_SEEK_CUR ? f->pos : f->length;
        return f->seek(base + offset);
    }
    static drwav_bool32 on_tell(void* self, drwav_int64* cursor) {
        *cursor = static_cast<File*>(self)->pos;
        return DRWAV_TRUE;
    }

    bool open(std::string& error) {
        opened = drwav_init(&wav, on_read, on_seek, on_tell, &file, nullptr);
        if (!opened || wav.channels == 0 || wav.sampleRate == 0) {
            error = "wav";
            return false;
        }
        audio_ = std::make_shared<Audio>(int(wav.sampleRate), std::min<int>(wav.channels, 2),
                                         int64_t(wav.totalPCMFrameCount));
        return true;
    }

    bool step(std::string&) override {
        buffer.resize(STEP_FRAMES * wav.channels);
        drwav_uint64 got = drwav_read_pcm_frames_f32(&wav, STEP_FRAMES, buffer.data());
        if (got == 0) return false;
        append(buffer.data(), size_t(got), wav.channels);
        return true;
    }
};

enum class Kind { OTHER, MP3, FLAC, WAV };

// Qué es, por sus primeros bytes (no por el nombre, que en Android a veces no se sabe)
Kind sniff(File& file) {
    uint8_t head[12] = {};
    file.seek(0);
    size_t n = file.read(head, sizeof head);
    file.seek(0);
    if (n >= 4 && std::memcmp(head, "fLaC", 4) == 0) return Kind::FLAC;
    if (n >= 12 && (std::memcmp(head, "RIFF", 4) == 0 || std::memcmp(head, "RF64", 4) == 0 ||
                    std::memcmp(head, "BW64", 4) == 0) && std::memcmp(head + 8, "WAVE", 4) == 0)
        return Kind::WAV;
    if (n >= 12 && std::memcmp(head, "FORM", 4) == 0 &&
        (std::memcmp(head + 8, "AIFF", 4) == 0 || std::memcmp(head + 8, "AIFC", 4) == 0))
        return Kind::WAV;
    // MP3: una etiqueta ID3 delante, o la marca de un cuadro MPEG de capa I a III (no un AAC en ADTS, capa 0)
    if (n >= 3 && std::memcmp(head, "ID3", 3) == 0) return Kind::MP3;
    if (n >= 2 && head[0] == 0xFF && (head[1] & 0xE0) == 0xE0 && (head[1] & 0x06) != 0) return Kind::MP3;
    return Kind::OTHER;
}

template <class D>
std::unique_ptr<Decoder> start_own(int fd, int64_t offset, int64_t length, std::string& error) {
    auto d = std::make_unique<D>();
    d->file.fd = ::dup(fd);
    d->file.offset = offset;
    d->file.length = length;
    if (!d->open(error)) return nullptr;
    d->start(true);
    return d;
}

}  // namespace

std::unique_ptr<Decoder> Decoder::open(int fd, int64_t offset, int64_t length, std::string& error) {
    if (length < 0) {
        struct stat st{};
        length = fstat(fd, &st) == 0 ? int64_t(st.st_size) - offset : 0;
    }
    Kind kind;
    {
        File probe;
        probe.fd = ::dup(fd);
        probe.offset = offset;
        probe.length = length;
        kind = sniff(probe);
    }
    std::unique_ptr<Decoder> d;
    std::string own_error;
    switch (kind) {
        case Kind::MP3: d = start_own<Mp3Decoder>(fd, offset, length, own_error); break;
        case Kind::FLAC: d = start_own<FlacDecoder>(fd, offset, length, own_error); break;
        case Kind::WAV: d = start_own<WavDecoder>(fd, offset, length, own_error); break;
        case Kind::OTHER: break;
    }
    if (d) return d;
    if (!own_error.empty()) LOG("own decoder (%s) failed: trying the codecs of Android", own_error.c_str());
#ifdef __ANDROID__
    return open_codec(fd, offset, length, error);
#else
    error = own_error.empty() ? "unknown format" : own_error;
    return nullptr;
#endif
}

}  // namespace slowdown
