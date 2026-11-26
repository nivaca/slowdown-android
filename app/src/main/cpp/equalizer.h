// SPDX-License-Identifier: GPL-3.0-or-later
// El ecualizador de py-slowdown (equalizer.py, en C++): cinco bandas, de 60 Hz a 12 kHz, de −12 a +12 dB.
//
// Las cinco ganancias son puntos de una curva: entre dos bandas va en línea
// recta (en decibeles, sobre la escala logarítmica de la frecuencia), y más allá
// de la primera y de la última sigue a su altura. La curva se vuelve un filtro
// FIR de fase lineal (la respuesta pedida, de la FFT al tiempo, centrada y
// suavizada con una ventana de Hann) y se aplica con la FFT de Rubber Band,
// bloque tras bloque, con la memoria de los últimos cuadros, como los filtros de
// Hearing. Atrasa el sonido medio largo de su núcleo (40 ms), que la exportación
// quita; plano, no se aplica. Un cambio de las ganancias pasa de la curva vieja
// a la nueva en un fundido, a lo largo de un bloque.
#pragma once

#include <array>
#include <map>
#include <memory>
#include <vector>

namespace slowdown {

constexpr int EQ_BANDS = 5;
constexpr int EQ_FREQUENCIES[EQ_BANDS] = {60, 250, 1000, 4000, 12000};  // Hz
constexpr int EQ_MAX_GAIN = 12;  // dB, arriba o abajo
constexpr double EQ_KERNEL_SECONDS = 0.08;  // más largo, más fiel en los graves (y más retraso)

using Gains = std::array<int, EQ_BANDS>;

Gains eq_clamp(const Gains& gains);
bool eq_flat(const Gains& gains);
int eq_size(int rate);                       // los coeficientes del filtro (impar)
int eq_delay(const Gains& gains, int rate);  // cuadros de retraso: 0 si es plano
double eq_response(const Gains& gains, double freq);  // la ganancia en dB de la curva, en FREQ
std::vector<float> eq_kernel(const Gains& gains, int rate);

class Equalizer {
public:
    explicit Equalizer(int rate);
    ~Equalizer();
    void set(const Gains& gains);
    const Gains& gains() const { return gains_; }
    void reset();  // olvida lo recibido (tras un salto)
    // FRAMES (intercalados, CH canales), ecualizados, en su lugar.
    void process(std::vector<float>& frames, int ch);

private:
    struct Spectrum;
    int rate_, size_;
    Gains gains_{};
    bool fading_ = false;
    Gains before_{};
    int history_channels_ = 0;
    std::vector<float> history_;
    std::map<std::pair<Gains, int>, std::unique_ptr<Spectrum>> spectra_;  // (ganancias, tamaño de la FFT)

    std::vector<float> apply(const std::vector<float>& whole, int count, int ch, const Gains& gains);
};

}  // namespace slowdown
