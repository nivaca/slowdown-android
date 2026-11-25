// SPDX-License-Identifier: GPL-3.0-or-later
// Lo mínimo para las pruebas del motor en el ordenador: CHECK y una señal de prueba.
#pragma once

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <vector>

#include "audio.h"

static int failures = 0;

#define CHECK(cond)                                                                 \
    do {                                                                            \
        if (!(cond)) {                                                              \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond); \
            ++failures;                                                             \
        }                                                                           \
    } while (0)

#define CHECK_NEAR(a, b, tol)                                                                     \
    do {                                                                                          \
        double a_ = (a), b_ = (b);                                                                \
        if (!(std::fabs(a_ - b_) <= (tol))) {                                                     \
            std::fprintf(stderr, "%s:%d: CHECK_NEAR failed: %s = %g, %s = %g\n", __FILE__, __LINE__, \
                         #a, a_, #b, b_);                                                         \
            ++failures;                                                                           \
        }                                                                                         \
    } while (0)

inline int finish() {
    if (failures) std::fprintf(stderr, "%d failure(s)\n", failures);
    return failures ? 1 : 0;
}

// Un tono de 440 Hz a la izquierda y ruido suave a la derecha, de SECONDS segundos, ya decodificado.
inline std::shared_ptr<slowdown::Audio> tone(double seconds, int rate = 48000, int channels = 2, bool done = true) {
    auto audio = std::make_shared<slowdown::Audio>(rate, channels, int64_t(seconds * rate));
    int64_t n = int64_t(seconds * rate);
    std::vector<float> frames(size_t(n * channels));
    uint32_t seed = 1;
    for (int64_t i = 0; i < n; ++i) {
        seed = seed * 1664525u + 1013904223u;
        frames[size_t(i * channels)] = 0.5f * float(std::sin(2 * M_PI * 440.0 * double(i) / rate));
        if (channels > 1) frames[size_t(i * channels + 1)] = 0.1f * (float(seed >> 8) / float(1 << 24) - 0.5f);
    }
    audio->append(frames.data(), n);
    if (done) audio->finish();
    return audio;
}
