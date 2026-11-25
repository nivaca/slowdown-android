// SPDX-License-Identifier: GPL-3.0-or-later
// El audio en memoria: los trozos, la lectura y los picos de la onda.
#include "check.h"

using slowdown::Audio;

int main() {
    // Más de un trozo, añadido en pedazos que no coinciden con los trozos
    const int64_t n = Audio::CHUNK * 2 + 1234;
    Audio audio(44100, 2, n);
    std::vector<float> all(size_t(n * 2));
    for (int64_t i = 0; i < n; ++i) {
        all[size_t(2 * i)] = float(i % 1000) / 1000.0f;
        all[size_t(2 * i + 1)] = -float(i % 1000) / 1000.0f;
    }
    CHECK(audio.length() == n);  // (lo que anuncia el archivo, antes de decodificarlo)
    for (int64_t at = 0; at < n; at += 9999) audio.append(all.data() + at * 2, std::min<int64_t>(9999, n - at));
    CHECK(audio.decoded() == n);
    CHECK(!audio.done());
    audio.finish();
    CHECK(audio.done());
    CHECK(audio.length() == n);

    // Una lectura que cruza el borde de un trozo
    std::vector<float> out(2000 * 2);
    int64_t got = audio.read(Audio::CHUNK - 1000, Audio::CHUNK + 1000, out.data());
    CHECK(got == 2000);
    bool same = true;
    for (int64_t i = 0; i < 2000 * 2; ++i) same &= out[size_t(i)] == all[size_t((Audio::CHUNK - 1000) * 2 + i)];
    CHECK(same);

    // Antes del principio y después del final: solo lo que hay
    got = audio.read(-500, 500, out.data());
    CHECK(got == 500);
    CHECK(out[0] == all[0]);
    got = audio.read(n - 10, n + 100, out.data());
    CHECK(got == 10);

    // Picos: la mezcla de los dos canales es 0 en todas partes
    std::vector<float> peaks(2 * 10);
    audio.peaks(0, 1000, 10, peaks.data());
    for (float p : peaks) CHECK(p == 0.0f);

    // Un solo canal: el mínimo y el máximo de cada columna, por los picos hechos y por las muestras
    Audio mono(8000, 1);
    std::vector<float> ramp(4096);
    for (int i = 0; i < 4096; ++i) ramp[size_t(i)] = float(i) / 4096.0f;
    mono.append(ramp.data(), 4096);
    audio.finish();
    mono.peaks(0, 1024, 4, peaks.data());
    CHECK_NEAR(peaks[0], 0.0, 1e-6);
    CHECK_NEAR(peaks[1], 1023 / 4096.0, 1e-6);
    CHECK_NEAR(peaks[7], 4095 / 4096.0, 1e-6);
    mono.peaks(100, 10, 2, peaks.data());
    CHECK_NEAR(peaks[0], 100 / 4096.0, 1e-6);
    CHECK_NEAR(peaks[3], 119 / 4096.0, 1e-6);
    // Después del final: vacío
    mono.peaks(5000, 100, 1, peaks.data());
    CHECK(peaks[0] == 0.0f && peaks[1] == 0.0f);
    return finish();
}
