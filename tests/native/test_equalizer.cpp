// SPDX-License-Identifier: GPL-3.0-or-later
// El ecualizador: la curva, la ganancia de cada banda, los bloques como una sola convolución, plano intacto, y
// un cambio sin chasquido.
#include <cmath>

#include "check.h"
#include "equalizer.h"

using namespace slowdown;

static std::vector<float> sine(double freq, int n, int rate, int ch = 2, float amplitude = 0.25f) {
    std::vector<float> out(size_t(n) * ch);
    for (int i = 0; i < n; ++i)
        for (int c = 0; c < ch; ++c) out[size_t(i * ch + c)] = amplitude * float(std::sin(2 * M_PI * freq * i / rate));
    return out;
}

static double rms(const std::vector<float>& v, int ch, int from, int to) {
    double sum = 0;
    for (int i = from; i < to; ++i) sum += double(v[size_t(i * ch)]) * v[size_t(i * ch)];
    return std::sqrt(sum / std::max(1, to - from));
}

// Por bloques de 1024, como el reproductor
static std::vector<float> run(Equalizer& eq, const std::vector<float>& in, int ch, int block = 1024) {
    std::vector<float> out;
    for (size_t at = 0; at < in.size(); at += size_t(block) * ch) {
        std::vector<float> part(in.begin() + long(at), in.begin() + long(std::min(in.size(), at + size_t(block) * ch)));
        eq.process(part, ch);
        out.insert(out.end(), part.begin(), part.end());
    }
    return out;
}

int main() {
    const int rate = 44100, n = rate;
    // La curva: en cada banda su ganancia; entre dos, en línea recta sobre el logaritmo; fuera, la de la punta
    Gains g{6, -3, 0, 9, -12};
    for (int i = 0; i < EQ_BANDS; ++i) CHECK_NEAR(eq_response(g, EQ_FREQUENCIES[i]), g[size_t(i)], 1e-9);
    CHECK_NEAR(eq_response(g, 500), 1.5 * -1.0, 1e-9);  // (a medio camino de 250 a 1000: −1,5)
    CHECK_NEAR(eq_response(g, 20), 6, 1e-9);
    CHECK_NEAR(eq_response(g, 20000), -12, 1e-9);
    CHECK(eq_size(rate) % 2 == 1 && eq_size(rate) == (int(EQ_KERNEL_SECONDS * rate) | 1));
    CHECK(eq_delay(Gains{}, rate) == 0 && eq_delay(g, rate) == eq_size(rate) / 2);
    CHECK(eq_clamp(Gains{20, -20, 3, 0, 0}) == (Gains{12, -12, 3, 0, 0}));

    // Plano: nada cambia
    {
        Equalizer eq(rate);
        auto in = sine(440, n, rate);
        CHECK(run(eq, in, 2) == in);
    }
    // Cada banda, sola, sube su frecuencia lo que dice (y deja casi igual una lejana)
    for (int band = 0; band < EQ_BANDS; ++band) {
        Gains one{};
        one[size_t(band)] = 6;
        Equalizer eq(rate);
        eq.set(one);
        auto in = sine(EQ_FREQUENCIES[band], n, rate);
        auto out = run(eq, in, 2);
        double gain = 20 * std::log10(rms(out, 2, rate / 4, n) / rms(in, 2, rate / 4, n));
        CHECK_NEAR(gain, 6.0, 0.5);
    }
    // Por bloques, lo mismo que de una vez (la memoria de un bloque al siguiente, sin costura)
    {
        Equalizer a(rate), b(rate);
        a.set(g);
        b.set(g);
        auto in = sine(300, n / 2, rate, 1);
        for (int i = 0; i < n / 2; ++i) in[size_t(i)] += 0.2f * float(std::sin(2 * M_PI * 5000 * i / rate));
        auto blocks = run(a, in, 1, 1024);
        auto whole = run(b, in, 1, 8192);
        double worst = 0;
        for (size_t i = 8192; i < blocks.size(); ++i) worst = std::max(worst, double(std::fabs(blocks[i] - whole[i])));
        CHECK(worst < 1e-4);
    }
    // Un cambio de las ganancias, sin chasquido: ningún salto de una muestra a la otra mucho mayor que los del tono
    {
        Equalizer eq(rate);
        eq.set(Gains{0, 0, 0, 0, 0});
        auto in = sine(200, n, rate, 1);
        std::vector<float> out;
        for (size_t at = 0; at < in.size(); at += 1024) {
            if (at == 20 * 1024) eq.set(Gains{12, 12, 0, 0, 0});
            std::vector<float> part(in.begin() + long(at), in.begin() + long(std::min(in.size(), at + 1024)));
            eq.process(part, 1);
            out.insert(out.end(), part.begin(), part.end());
        }
        double step = 0;
        for (size_t i = 1; i < out.size(); ++i) step = std::max(step, double(std::fabs(out[i] - out[i - 1])));
        double tone = 0.25 * 2 * M_PI * 200 / rate * std::pow(10.0, 12 / 20.0);  // (el salto mayor del tono, a +12 dB)
        CHECK(step < tone * 1.3);
    }
    return finish();
}
