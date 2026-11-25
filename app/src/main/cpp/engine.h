// SPDX-License-Identifier: GPL-3.0-or-later
// La reproducción: el audio a otra velocidad, sin cambiar su altura.
//
// Es el motor de py-slowdown (engine.py), pasado a C++. Renderer produce el
// audio de salida por bloques, a partir de un Audio, de una velocidad y de un
// cambio de altura en semitonos. A velocidad 1 y sin cambio de altura copia las
// muestras tal cual (bit a bit); si no, las pasa por el motor R3 («finer») de
// Rubber Band, en modo de tiempo real.
//
// Player lo lleva a la salida de sonido: un hilo productor llena una cola corta
// (0,15 segundos) con bloques de Renderer, y la función que llama la salida
// (AAudio en Android) toma de esa cola. Cada bloque lleva la posición del
// archivo donde empieza y cuántos cuadros del archivo vale cada cuadro de
// salida, de modo que la posición que se muestra es la que suena. Los cortes
// (pausa, salto, cambio entre copia y Rubber Band) se suavizan con rampas de
// pocos milisegundos, para que no chasqueen.

#pragma once

#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>
#include <utility>
#include <vector>

#include "audio.h"
#include "hearing.h"

namespace RubberBand { class RubberBandStretcher; }

namespace slowdown {

constexpr double MIN_SPEED = 0.25;
constexpr double MAX_SPEED = 3.0;
constexpr int BLOCK = 1024;            // output frames per block
constexpr int FADE = 192;              // cuadros de cada rampa contra chasquidos (unos 4 ms)
constexpr double QUEUE_SECONDS = 0.15; // audio preparado de antemano: también el retraso de un cambio de velocidad
constexpr double MAX_PITCH = 12.0;     // semitonos, arriba o abajo
constexpr double LOOP_FADE = 0.01;     // segundos del fundido con que el final de una selección entra en su principio
constexpr double MIN_LOOP = 0.1;       // seconds: a shorter selection is not repeated
constexpr int MIN_BPM = 20, MAX_BPM = 300, MAX_BEATS = 16;

enum Quality { QUALITY_HIGH = 0, QUALITY_LIGHT = 1, QUALITY_R2 = 2 };

double clamp_speed(double speed);
double clamp_pitch(double semitones);
double pitch_scale(double semitones);

// Output frames, and where they come from in the file.
struct Block {
    std::vector<float> frames;  // intercalados: n × canales
    int channels = 1;
    double start = 0;           // cuadro del archivo donde empieza
    double step = 1;            // cuadros del archivo por cuadro de salida (la velocidad); 0 en la cuenta previa
    int64_t used = 0;           // cuadros ya tocados
    bool count = false;         // si es una cuenta previa
    int64_t lead = 0, beat = 0, beats = 0;  // (silencio inicial, cuadros por pulso, pulsos) de la cuenta

    int64_t size() const { return int64_t(frames.size()) / channels; }
    double position() const { return start + double(used) * step; }
};

double max_earlier(int rate, double bpm);
Block count_in(int rate, int channels, int beats, double bpm, double shift = 0.0);

class Renderer {
public:
    explicit Renderer(std::shared_ptr<Audio> audio);
    ~Renderer();

    void set_loop(std::optional<std::pair<int64_t, int64_t>> loop);
    void seek(double frame, bool fade = true);
    void set_speed(double speed);
    void set_pitch(double semitones);
    void set_quality(int quality);
    void set_formants(bool preserved);
    bool plain() const { return speed_ == 1.0 && pitch_ == 0.0; }

    // The next block of at most FRAMES frames, and whether it starts after a cut. No block at the end of the
    // file; an empty one while the frames it needs are still being decoded.
    std::optional<Block> render(int frames, bool& seam);

    double pos() const { return pos_; }

private:
    std::shared_ptr<Audio> audio_;
    double speed_ = 1.0, pitch_ = 0.0;
    bool formants_ = false;
    int quality_ = QUALITY_HIGH;
    double pos_ = 0;  // cuadro del archivo que corresponde al próximo cuadro de salida
    std::unique_ptr<RubberBand::RubberBandStretcher> stretcher_;
    bool stretching_ = false;  // si el estirador lleva ahora el audio
    int64_t feed_ = 0;         // el próximo cuadro del archivo que recibe el estirador
    bool final_ = false;       // el estirador recibió el final del archivo
    int64_t discard_ = 0;      // cuadros de salida que se tiran (el retraso del estirador)
    bool seam_ = true;         // el bloque siguiente empieza después de un corte
    std::optional<std::pair<int64_t, int64_t>> loop_;
    int64_t xfade_;
    std::vector<float> planar_;         // memoria de trabajo para el estirador
    std::vector<float*> pointers_;

    std::vector<float> read(int64_t start, int64_t frames);
    bool wraps(double before, double after) const;
    void changed(bool was_plain);
    std::optional<Block> copy(int frames);
    std::optional<Block> stretch(int frames);
    void restart();
    void process(const std::vector<float>& interleaved, bool final);
    std::vector<float> retrieve(int64_t frames);
    Block empty(double start, double step) const;
};

// Where the audio goes.
class Output {
public:
    using Callback = std::function<void(float* out, int frames)>;
    virtual ~Output() = default;
    virtual bool open(int rate, int channels, Callback callback) = 0;
    virtual void close() = 0;
    virtual double latency() = 0;   // segundos entre lo que sale de la cola y lo que se oye
    virtual void pause() {}          // sin reproducir: la salida puede dormir
    virtual void resume() {}
    virtual bool lost() { return false; }  // la salida se perdió (se desconectaron los auriculares…)
};

class Player {
public:
    explicit Player(std::unique_ptr<Output> output);
    ~Player();

    bool load(std::shared_ptr<Audio> audio);
    void unload();
    std::shared_ptr<Audio> audio() const { return audio_; }

    void play(int beats = 0, double bpm = 0, double offset = 0);
    void pause();
    void stop();
    void seek(double seconds);
    void set_loop(std::optional<std::pair<double, double>> loop);
    std::optional<std::pair<double, double>> loop() const;
    // (the beat that sounds, of how many) during a count-in, 0 before the first click; nothing otherwise.
    std::optional<std::pair<int, int>> counting();

    void set_speed(double speed);
    void set_pitch(double semitones);
    void set_formants(bool preserved);
    void set_quality(int quality);
    void set_volume(double volume) { volume_.store(float(volume)); }
    void set_hearing(int channels, int filter);  // cómo se oye (hearing.h)
    double speed() const { return speed_; }
    double pitch() const { return pitch_; }

    bool playing() const { return playing_.load(); }
    bool take_ended() { return ended_.exchange(false); }  // llegó al final (la interfaz lo lee una vez)
    double position();
    double duration() const { return audio_ ? audio_->duration() : 0.0; }

    // Para las pruebas: la función que llamaría la salida.
    void callback(float* out, int frames);

private:
    std::unique_ptr<Output> output_;
    std::shared_ptr<Audio> audio_;
    std::unique_ptr<Renderer> renderer_;
    std::atomic<bool> playing_{false};
    std::atomic<bool> ended_{false};
    std::atomic<float> volume_{1.0f};
    std::optional<std::pair<double, double>> loop_;  // the selection repeated, in seconds
    double speed_ = 1.0, pitch_ = 0.0;
    bool formants_ = false;
    int quality_ = QUALITY_HIGH;
    int hearing_channels_ = BOTH, hearing_filter_ = NONE;
    std::unique_ptr<Hearing> hearing_;

    mutable std::mutex lock_;
    std::condition_variable wake_;
    std::deque<Block> queue_;
    int64_t queued_ = 0;     // output frames in the queue
    double position_ = 0;    // file frame playing now
    float gain_ = 0;         // ganancia actual de la rampa de pausa
    bool eof_ = false;
    struct Request { int kind; double a, b; };
    std::vector<Request> requests_;
    std::thread thread_;
    bool running_ = false;
    bool sleeping_ = false;  // la salida está en pausa

    std::mutex control_;     // para abrir, cerrar, dormir y despertar la salida, de a uno
    void control(bool sleep);
    void request(int kind, double a = 0, double b = 0);
    void produce();
    int64_t target() const;
    void cut_queue(int64_t keep);
    void fade_tail();
};

}  // namespace slowdown
