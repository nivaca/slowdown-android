// SPDX-License-Identifier: GPL-3.0-or-later
// Cómo se oye la grabación: qué canales y qué parte del espectro (hearing.py de py-slowdown, en C++).
//
// Para sacar de oído una parte de una mezcla sirve oírla sola, o casi: un canal
// (lo que se grabó a un lado), los dos en uno (mono), la mezcla sin lo que está en
// el centro (la diferencia de los canales: la voz y el solo suelen estar allí),
// solo los graves (la línea del bajo) o todo menos los graves.
//
// Hearing hace eso con los bloques de salida, uno tras otro. Los canales se
// mezclan cuadro por cuadro; los filtros son FIR de fase lineal, aplicados con
// la FFT de Rubber Band, con la memoria de los últimos cuadros recibidos, de modo
// que un bloque sigue al anterior sin costura. Un filtro retrasa el sonido medio
// largo de su núcleo (unos 20 ms); al cambiar de modo, el bloque siguiente pasa
// del uno al otro en un fundido, sin chasquido.
#pragma once

#include <map>
#include <memory>
#include <vector>

namespace slowdown {

enum Channels { BOTH = 0, LEFT, RIGHT, MONO, NO_CENTRE };
enum Filter { NONE = 0, BASS, NO_BASS };

constexpr double CROSSOVER = 250.0;      // Hz: debajo, «los graves»
constexpr double KERNEL_SECONDS = 0.04;  // el largo del núcleo: más largo, un corte más neto y más retraso

// The taps of a linear-phase FIR filter: BASS lets through what is below CROSSOVER, NO_BASS what is above.
std::vector<float> kernel(int filter, int rate);

class Hearing {
public:
    explicit Hearing(int rate);
    ~Hearing();
    void set(int channels, int filter);
    void reset();  // olvida lo recibido (tras un salto: lo de antes no es lo que sigue)
    bool plain() const { return channels_ == BOTH && filter_ == NONE; }
    // FRAMES (intercalados, CH canales) como se han de oír, en su lugar.
    void process(std::vector<float>& frames, int ch);

private:
    struct Spectrum;
    int rate_, size_;
    int channels_ = BOTH, filter_ = NONE;
    bool fading_ = false;
    int before_channels_ = BOTH, before_filter_ = NONE;
    int history_channels_ = 0;
    std::vector<float> history_;  // los últimos size_ - 1 cuadros recibidos, sin filtrar, intercalados
    std::map<std::pair<int, int>, std::unique_ptr<Spectrum>> spectra_;  // (filtro, tamaño de la FFT)

    std::vector<float> apply(const std::vector<float>& whole, int count, int ch, int channels, int filter);
};

}  // namespace slowdown
