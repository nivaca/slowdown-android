// SPDX-License-Identifier: GPL-3.0-or-later
// La exportación: la grabación, o una parte, como suena (a la velocidad y la altura elegidas, y como se oye,
// si se pide), en un archivo WAV (PCM de 16 bits) o M4A (AAC, con el codificador de Android).
//
// Se hace fuera de la reproducción, con un Renderer propio sobre el mismo Audio (que debe estar leído hasta su
// final), en un hilo: Export dice cuánto lleva, se puede cancelar, y dice si hubo un error.
#pragma once

#include <atomic>
#include <memory>
#include <string>
#include <thread>

#include "audio.h"

namespace slowdown {

enum ExportFormat { EXPORT_WAV = 0, EXPORT_M4A = 1 };

struct ExportSettings {
    int format = EXPORT_WAV;
    double start = 0, end = -1;  // segundos de la grabación; END < 0: hasta el final
    double speed = 1.0, pitch = 0.0;
    bool formants = false;
    int quality = 0;
    int channels = 0, filter = 0;  // cómo se oye (hearing.h)
    double gain = 1.0;
};

class Export {
public:
    // FD es del que llama (se duplica); debe poder volver atrás (un archivo, no una tubería).
    Export(std::shared_ptr<Audio> audio, int fd, ExportSettings settings);
    ~Export();
    double progress() const { return progress_.load(); }  // de 0 a 1
    bool done() const { return done_.load(); }
    const std::string& error() const { return error_; }  // (léase cuando done)
    void cancel() { cancel_ = true; }

    // Para las pruebas y el hilo: hace todo, aquí mismo
    void run();

private:
    std::shared_ptr<Audio> audio_;
    int fd_;
    ExportSettings settings_;
    std::atomic<double> progress_{0};
    std::atomic<bool> done_{false}, cancel_{false};
    std::string error_;
    std::thread thread_;
    friend std::unique_ptr<Export> start_export(std::shared_ptr<Audio>, int, ExportSettings);
};

std::unique_ptr<Export> start_export(std::shared_ptr<Audio> audio, int fd, ExportSettings settings);

}  // namespace slowdown
