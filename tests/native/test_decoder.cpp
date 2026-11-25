// SPDX-License-Identifier: GPL-3.0-or-later
// Los decodificadores propios (MP3, FLAC, WAV): el formato, la duración, el tono y la mezcla de más de dos
// canales. Los archivos de data/ son un segundo de La 440, hechos con FFmpeg.
#include <fcntl.h>
#include <unistd.h>

#include <chrono>
#include <string>
#include <thread>

#include "check.h"
#include "decoder.h"

using namespace slowdown;

static std::shared_ptr<Audio> decode(const std::string& name, std::string& error, int64_t offset = 0) {
    std::string path = std::string(DATA) + "/" + name;
    int fd = ::open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        error = "missing " + path;
        return nullptr;
    }
    auto decoder = Decoder::open(fd, offset, -1, error);
    ::close(fd);  // (el decodificador tiene su propia copia)
    if (!decoder) return nullptr;
    auto audio = decoder->audio();
    for (int i = 0; i < 2000 && !audio->done(); ++i) std::this_thread::sleep_for(std::chrono::milliseconds(1));
    CHECK(audio->done());
    CHECK(!audio->failed());
    return audio;
}

static int crossings(const Audio& audio) {
    std::vector<float> frames(size_t(audio.decoded() * audio.channels));
    audio.read(0, audio.decoded(), frames.data());
    int count = 0;
    for (int64_t i = 1000; i + 1 < audio.decoded() - 1000; ++i)
        count += (frames[size_t(i * audio.channels)] < 0) != (frames[size_t((i + 1) * audio.channels)] < 0);
    return count;
}

int main() {
    for (const char* name : {"tone.mp3", "tone.flac", "tone.wav"}) {
        std::string error;
        auto audio = decode(name, error);
        CHECK(audio != nullptr);
        if (!audio) {
            std::fprintf(stderr, "%s: %s\n", name, error.c_str());
            continue;
        }
        CHECK(audio->rate == 44100);
        CHECK(audio->channels == 2);
        // Un segundo: el MP3 sin el retraso ni el relleno del codificador (la etiqueta de LAME lo dice)
        CHECK_NEAR(double(audio->decoded()), 44100.0, 2.0);
        CHECK(audio->length() == audio->decoded());
        double seconds = double(audio->decoded() - 2000) / audio->rate;
        CHECK_NEAR(crossings(*audio) / 2.0 / seconds, 440.0, 2.0);
    }
    // 5.1: se mezcla en estéreo, sin pasarse de 1
    {
        std::string error;
        auto audio = decode("tone-51.wav", error);
        CHECK(audio && audio->channels == 2 && audio->rate == 48000);
        if (audio) {
            float loudest = 0;
            std::vector<float> peaks(2 * 10);
            audio->peaks(0, 1200, 10, peaks.data());
            for (float p : peaks) loudest = std::max(loudest, std::fabs(p));
            CHECK(loudest > 0.05f && loudest <= 1.0f);
        }
    }
    // Lo que no es MP3, FLAC ni WAV va a los códecs de Android: en el ordenador, un error
    {
        std::string error;
        int fd = ::open((std::string(DATA) + "/tone.m4a").c_str(), O_RDONLY);
        CHECK(Decoder::open(fd, 0, -1, error) == nullptr);
        CHECK(!error.empty());
        ::close(fd);
    }
    return finish();
}
