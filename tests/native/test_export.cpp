// SPDX-License-Identifier: GPL-3.0-or-later
// La exportación a WAV: la cabecera, a 1× las mismas muestras, a 0,5× el doble, una selección, y con un filtro
// el mismo largo (el retraso del núcleo, compensado).
#include <fcntl.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include "check.h"
#include "exporter.h"
#include "hearing.h"

using namespace slowdown;

static std::string path(const char* name) { return std::string("/tmp/slowdown-export-test-") + name + ".wav"; }

// (cuadros, canales, frecuencia) de un WAV de 16 bits, y sus muestras
static int64_t read_wav(const std::string& file, int& channels, int& rate, std::vector<int16_t>& samples) {
    FILE* f = std::fopen(file.c_str(), "rb");
    if (!f) return -1;
    uint8_t h[44];
    if (std::fread(h, 1, 44, f) != 44 || std::memcmp(h, "RIFF", 4) || std::memcmp(h + 8, "WAVEfmt ", 8)) {
        std::fclose(f);
        return -1;
    }
    channels = h[22];
    rate = int(h[24] | h[25] << 8 | h[26] << 16 | uint32_t(h[27]) << 24);
    uint32_t data = uint32_t(h[40] | h[41] << 8 | h[42] << 16 | uint32_t(h[43]) << 24);
    samples.resize(data / 2);
    size_t got = std::fread(samples.data(), 2, samples.size(), f);
    std::fclose(f);
    return got == samples.size() ? int64_t(samples.size()) / channels : -1;
}

static int64_t run(std::shared_ptr<Audio> audio, ExportSettings s, const char* name, std::vector<int16_t>& out) {
    std::string file = path(name);
    int fd = ::open(file.c_str(), O_RDWR | O_CREAT | O_TRUNC, 0600);
    Export job(audio, fd, s);
    ::close(fd);
    job.run();
    CHECK(job.done());
    CHECK(job.error().empty());
    if (!job.error().empty()) std::fprintf(stderr, "%s: %s\n", name, job.error().c_str());
    int ch = 0, rate = 0;
    int64_t frames = read_wav(file, ch, rate, out);
    CHECK(ch == audio->channels && rate == audio->rate);
    ::unlink(file.c_str());
    return frames;
}

int main() {
    auto audio = tone(2.0, 44100, 2);
    std::vector<int16_t> out;
    // A 1×: las muestras del archivo, en 16 bits
    ExportSettings s;
    int64_t frames = run(audio, s, "plain", out);
    CHECK(frames == audio->decoded());
    std::vector<float> original(size_t(audio->decoded()) * 2);
    audio->read(0, audio->decoded(), original.data());
    int worst = 0;
    for (size_t i = 0; i < out.size(); ++i)
        worst = std::max(worst, std::abs(int(out[i]) - int(std::lrint(original[i] * 32767.0f))));
    CHECK(worst == 0);
    // A 0,5×: el doble, más o menos lo que retiene Rubber Band
    s.speed = 0.5;
    frames = run(audio, s, "slow", out);
    CHECK_NEAR(double(frames) / audio->rate, 4.0, 0.15);
    // Una selección, de 0,5 a 1,5 s
    s.speed = 1.0;
    s.start = 0.5;
    s.end = 1.5;
    frames = run(audio, s, "part", out);
    CHECK(frames == 44100);
    CHECK(out[0] == int16_t(std::lrint(original[size_t(22050 * 2)] * 32767.0f)));
    // Con un filtro, el mismo largo
    s.filter = BASS;
    frames = run(audio, s, "bass", out);
    CHECK(frames == 44100);
    return finish();
}
