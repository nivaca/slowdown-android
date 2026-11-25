// SPDX-License-Identifier: GPL-3.0-or-later
// La decodificación de un archivo: open() mira sus primeros bytes y elige cómo.
//
// MP3, FLAC y WAV se decodifican dentro de la aplicación (minimp3, dr_flac y
// dr_wav): con los códecs de Android cada búfer va y vuelve de otro proceso, y
// en un Moto g55 eso deja el MP3 en unas 13 veces el tiempo real y el FLAC por
// debajo del tiempo real. Lo demás (AAC, Opus, Vorbis, el sonido de un video…)
// va a los códecs de Android (MediaExtractor y MediaCodec del NDK).
//
// open() devuelve el decodificador cuando ya se conoce el formato (la
// frecuencia y los canales verdaderos), con un Audio vacío; un hilo sigue
// decodificando y lo llena, de modo que el archivo se puede tocar mientras se
// lee. Los archivos de más de dos canales se mezclan en estéreo.
#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <string>
#include <thread>
#include <vector>

#include "audio.h"

namespace slowdown {

// Un archivo abierto, leído con pread desde OFFSET, LENGTH bytes (un trozo de un descriptor de Android).
struct File {
    int fd = -1;
    int64_t offset = 0, length = 0, pos = 0;
    size_t read(void* buffer, size_t bytes);
    bool seek(int64_t to);
    ~File();
};

class Decoder {
public:
    virtual ~Decoder() = default;
    // FD es del que llama (se duplica). Devuelve nullptr y ERROR si no se puede leer.
    static std::unique_ptr<Decoder> open(int fd, int64_t offset, int64_t length, std::string& error);
    std::shared_ptr<Audio> audio() const { return audio_; }
    virtual const char* kind() const = 0;
    void start(bool more);  // el hilo que sigue hasta el final

protected:
    std::shared_ptr<Audio> audio_;
    std::thread thread_;
    std::atomic<bool> cancel_{false};

    // Un paso: decodifica un poco y lo añade a audio_; false al final (ERROR dice si fue un error).
    virtual bool step(std::string& error) = 0;
    void stop();            // lo detiene: cada subclase lo llama en su destructor, antes de soltar lo suyo
    // Añade FRAMES cuadros de IN canales (intercalados), mezclados en estéreo si son más de dos.
    void append(const float* samples, size_t frames, int in);

private:
    std::vector<float> mixed_;
};

std::unique_ptr<Decoder> open_codec(int fd, int64_t offset, int64_t length, std::string& error);

// FRAMES cuadros de IN canales (intercalados) en OUT, mezclados en estéreo si son más de dos; si no, tal cual.
void mix_to_stereo(const float* samples, size_t frames, int in, std::vector<float>& out);

}  // namespace slowdown
