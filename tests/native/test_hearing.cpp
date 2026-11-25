// SPDX-License-Identifier: GPL-3.0-or-later
// Cómo se oye: los núcleos de los filtros, los canales, el filtro de los graves y el paso de un bloque al otro.
#include <cmath>

#include "check.h"
#include "hearing.h"

using namespace slowdown;

static std::vector<float> stereo(double fl, double fr, int n, int rate, float al = 0.5f, float ar = 0.5f) {
    std::vector<float> out(size_t(n) * 2);
    for (int i = 0; i < n; ++i) {
        out[size_t(2 * i)] = al * float(std::sin(2 * M_PI * fl * i / rate));
        out[size_t(2 * i + 1)] = ar * float(std::sin(2 * M_PI * fr * i / rate));
    }
    return out;
}

static double rms(const std::vector<float>& v, int ch, int c, int from) {
    double sum = 0;
    int n = 0;
    for (size_t i = size_t(from) * ch + c; i < v.size(); i += ch, ++n) sum += double(v[i]) * v[i];
    return std::sqrt(sum / std::max(1, n));
}

// Por bloques de 1024, como el reproductor
static std::vector<float> run(Hearing& h, std::vector<float> in, int ch) {
    std::vector<float> out;
    for (size_t at = 0; at < in.size(); at += 1024 * ch) {
        std::vector<float> block(in.begin() + at, in.begin() + std::min(in.size(), at + 1024 * ch));
        h.process(block, ch);
        out.insert(out.end(), block.begin(), block.end());
    }
    return out;
}

int main() {
    const int rate = 44100, n = rate;
    // Los núcleos: los graves dejan pasar la continua entera; sin los graves, nada de ella
    auto bass = kernel(BASS, rate), high = kernel(NO_BASS, rate);
    CHECK(bass.size() % 2 == 1 && bass.size() == size_t(int(KERNEL_SECONDS * rate) | 1));
    double sb = 0, sh = 0;
    for (float v : bass) sb += v;
    for (float v : high) sh += v;
    CHECK_NEAR(sb, 1.0, 1e-5);
    CHECK_NEAR(sh, 0.0, 1e-5);

    // Tal cual: nada cambia
    {
        Hearing h(rate);
        auto in = stereo(440, 660, n, rate);
        CHECK(run(h, in, 2) == in);
    }
    // Un canal, mono, sin el centro
    {
        Hearing h(rate);
        h.set(LEFT, NONE);
        auto out = run(h, stereo(440, 660, n, rate), 2);
        CHECK_NEAR(rms(out, 2, 1, 2048), 0.5 / std::sqrt(2.0), 0.01);  // (la derecha es ahora la izquierda)
        Hearing centre(rate);
        centre.set(NO_CENTRE, NONE);
        auto same = run(centre, stereo(440, 440, n, rate), 2);  // lo mismo a los dos lados: se anula
        CHECK(rms(same, 2, 0, 2048) < 1e-4);
    }
    // Solo los graves: pasa 100 Hz, se queda 2 kHz; sin los graves, al revés
    {
        Hearing low(rate);
        low.set(BOTH, BASS);
        auto out = run(low, stereo(100, 2000, n, rate), 2);
        CHECK_NEAR(rms(out, 2, 0, 4096), 0.5 / std::sqrt(2.0), 0.02);
        CHECK(rms(out, 2, 1, 4096) < 0.005);
        Hearing hi(rate);
        hi.set(BOTH, NO_BASS);
        out = run(hi, stereo(100, 2000, n, rate), 2);
        CHECK(rms(out, 2, 0, 4096) < 0.01);
        CHECK_NEAR(rms(out, 2, 1, 4096), 0.5 / std::sqrt(2.0), 0.02);
    }
    // Por bloques, sin costuras: igual que el filtro aplicado de una vez (con el retraso del núcleo)
    {
        Hearing h(rate);
        h.set(BOTH, BASS);
        auto in = stereo(150, 150, n / 4, rate);
        auto out = run(h, in, 2);
        int delay = int(bass.size()) / 2;
        double worst = 0;
        for (int i = int(bass.size()); i < n / 4; ++i) {
            double direct = 0;
            for (size_t k = 0; k < bass.size(); ++k) direct += double(bass[k]) * in[size_t((i - int(k)) * 2)];
            worst = std::max(worst, std::fabs(direct - out[size_t(i * 2)]));
        }
        CHECK(worst < 1e-4);
        CHECK(delay > 0);
    }
    return finish();
}
