// SPDX-License-Identifier: GPL-3.0-or-later
// La decodificación de un archivo con los códecs de Android (MediaExtractor y MediaCodec del NDK).
//
// open() lee el archivo hasta conocer el formato de lo que sale del códec (la
// frecuencia y los canales verdaderos: un AAC HE anuncia a veces la mitad) y
// devuelve un Audio vacío; un hilo sigue decodificando y lo llena, de modo que
// el archivo se puede tocar mientras se lee. Los archivos de más de dos canales
// se mezclan en estéreo.
#pragma once

#include <atomic>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include "audio.h"

struct AMediaExtractor;
struct AMediaCodec;
struct AMediaFormat;

namespace slowdown {

class Decoder {
public:
    ~Decoder();
    // FD es del que llama (se duplica). Devuelve nullptr y ERROR si no se puede leer.
    static std::unique_ptr<Decoder> open(int fd, int64_t offset, int64_t length, std::string& error);
    std::shared_ptr<Audio> audio() const { return audio_; }

private:
    Decoder() = default;
    int fd_ = -1;
    AMediaExtractor* extractor_ = nullptr;
    AMediaCodec* codec_ = nullptr;
    std::shared_ptr<Audio> audio_;
    std::thread thread_;
    std::atomic<bool> cancel_{false};
    bool input_done_ = false;
    int64_t expected_us_ = 0;
    int encoding_ = 2;  // la codificación de lo que sale del códec (2: 16 bits, 4: float…)
    int in_channels_ = 2;
    std::vector<float> scratch_;

    // Un paso del bucle; devuelve false al final (o en un error).
    bool step(std::string& error);
    void output_format();
    void deliver(const uint8_t* data, size_t size);
};

}  // namespace slowdown
