// SPDX-License-Identifier: GPL-3.0-or-later
// El audio decodificado de un archivo, en memoria, que crece mientras se decodifica.
//
// Las muestras se guardan intercaladas (L R L R…), en trozos de CHUNK cuadros que
// no se mueven una vez hechos: el decodificador añade al final mientras el
// reproductor y la vista leen lo que ya está, sin cerrojo. `decoded` dice hasta
// dónde se puede leer. Cada trozo lleva además, por cada PEAK cuadros, el mínimo y
// el máximo de la mezcla de sus canales, para dibujar la onda.

#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <vector>

namespace slowdown {

class Audio {
public:
    static constexpr int CHUNK_SHIFT = 16;
    static constexpr int64_t CHUNK = int64_t(1) << CHUNK_SHIFT;  // cuadros por trozo
    static constexpr int PEAK = 256;                             // cuadros por pico de la onda
    static constexpr int64_t MAX_CHUNKS = 16384;                 // unas 6 horas a 48 kHz

    Audio(int rate, int channels, int64_t expected = 0);
    ~Audio();

    const int rate;
    const int channels;

    // Lo que el decodificador hace: añadir cuadros intercalados, y decir que acabó.
    void append(const float* frames, int64_t count);
    void finish(bool failed = false);

    int64_t decoded() const { return decoded_.load(std::memory_order_acquire); }
    bool done() const { return done_.load(std::memory_order_acquire); }
    bool failed() const { return failed_.load(std::memory_order_acquire); }
    // Los cuadros que tendrá: los que anuncia el archivo mientras se decodifica, los decodificados al final.
    int64_t length() const;
    double duration() const { return double(length()) / rate; }

    // Copia en OUT (intercalados) los cuadros de START a END que ya están; devuelve cuántos.
    // START puede ser negativo: entonces se copian solo los de 0 en adelante (como audio.frames en Python).
    int64_t read(int64_t start, int64_t end, float* out) const;

    // Para la onda: el mínimo y el máximo de COUNT columnas de FPP cuadros cada una, desde el cuadro START.
    // OUT recibe 2 × COUNT valores (mín, máx); las columnas sin audio quedan en 0.
    void peaks(double start, double fpp, int count, float* out) const;

private:
    struct Chunk {
        std::unique_ptr<float[]> frames;
        float lo[CHUNK / PEAK];
        float hi[CHUNK / PEAK];
    };
    std::unique_ptr<std::atomic<Chunk*>[]> chunks_;
    std::atomic<int64_t> decoded_{0};
    std::atomic<bool> done_{false};
    std::atomic<bool> failed_{false};
    std::atomic<int64_t> expected_{0};
    float pending_lo_ = 0, pending_hi_ = 0;  // el pico a medio hacer (solo el decodificador los toca)

    const float* at(int64_t frame) const;
};

}  // namespace slowdown
