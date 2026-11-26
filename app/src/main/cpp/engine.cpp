// SPDX-License-Identifier: GPL-3.0-or-later
#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>

#include "rubberband/RubberBandStretcher.h"

namespace slowdown {

using RubberBand::RubberBandStretcher;

namespace {

constexpr double PI = 3.14159265358979323846;
constexpr int MAX_PROCESS = 4096;

// La cuenta previa empieza con un soplo casi inaudible (unos -54 dB) y lo mantiene, más bajo, entre los golpes:
// muchos auriculares Bluetooth se silencian mientras reciben silencio y tardan en despertar, y se tragaban los
// golpes, cortos y aislados. El soplo los despierta antes del primero.
constexpr double COUNT_LEAD = 0.4;   // segundos antes del primer golpe
constexpr float WAKE_LEVEL = 0.002f; // el soplo del principio
constexpr float KEEP_LEVEL = 0.0008f; // el soplo entre los golpes
constexpr double CLICK = 0.06;       // duración de cada golpe de la cuenta

constexpr RubberBandStretcher::Options COMMON = RubberBandStretcher::OptionProcessRealTime |
                                                RubberBandStretcher::OptionChannelsTogether |
                                                RubberBandStretcher::OptionPitchHighConsistency;

// La calidad del estiramiento: lo que cuesta al procesador contra lo que se oye.
RubberBandStretcher::Options options(int quality) {
    switch (quality) {
        case QUALITY_LIGHT:  // R3 with a short window: half the work
            return COMMON | RubberBandStretcher::OptionEngineFiner | RubberBandStretcher::OptionWindowShort;
        case QUALITY_R2:  // the older engine: crisp attacks
            return COMMON | RubberBandStretcher::OptionEngineFaster | RubberBandStretcher::OptionTransientsCrisp;
        default:  // R3, the best
            return COMMON | RubberBandStretcher::OptionEngineFiner | RubberBandStretcher::OptionWindowStandard;
    }
}

enum Kind { SEEK, SPEED, LOOP, PITCH, FORMANTS, QUALITY, HEARING, EQUALIZER };

}  // namespace

double clamp_speed(double speed) {
    return std::min(MAX_SPEED, std::max(MIN_SPEED, std::round(speed * 1000.0) / 1000.0));
}

double clamp_pitch(double semitones) {
    return std::min(MAX_PITCH, std::max(-MAX_PITCH, std::round(semitones * 100.0) / 100.0));
}

double pitch_scale(double semitones) { return std::pow(2.0, semitones / 12.0); }

double max_earlier(int rate, double bpm) {
    bpm = std::min<double>(std::max<double>(MIN_BPM, bpm), MAX_BPM);
    return std::max(0.0, double(std::llround(rate * 60.0 / bpm) - int64_t((CLICK + 0.01) * rate)) / rate);
}

Block count_in(int rate, int channels, int beats, double bpm, double shift) {
    beats = std::min(std::max(1, beats), MAX_BEATS);
    bpm = std::min<double>(std::max<double>(MIN_BPM, bpm), MAX_BPM);
    int64_t lead = int64_t(COUNT_LEAD * rate);
    int64_t beat = std::llround(rate * 60.0 / bpm);
    int64_t shift_frames = std::max<int64_t>(std::llround(shift * rate), -std::llround(max_earlier(rate, bpm) * rate));
    int64_t length = lead + beats * beat + shift_frames;
    Block block;
    block.channels = channels;
    block.frames.assign(size_t(length * channels), 0.0f);
    uint32_t seed = 7;
    for (int64_t i = 0; i < length; ++i) {
        seed = seed * 1664525u + 1013904223u;  // (un ruido cualquiera, siempre el mismo)
        float noise = float(seed >> 8) / float(1 << 24) * 2.0f - 1.0f;
        float value = noise * (i < lead ? WAKE_LEVEL : KEEP_LEVEL);
        for (int c = 0; c < channels; ++c) block.frames[size_t(i * channels + c)] = value;
    }
    int64_t click_frames = int64_t(CLICK * rate);
    int64_t attack = int64_t(0.002 * rate);
    for (int n = 0; n < beats; ++n) {
        // Un golpe de madera, más o menos: la nota y su octava, que se apagan pronto
        double pitch = n == 0 ? 1760.0 : 1320.0;
        double level = n == 0 ? 0.55 : 0.4;
        int64_t start = lead + n * beat;
        for (int64_t i = 0; i < click_frames && start + i < length; ++i) {
            double t = double(i) / rate;
            double envelope = std::exp(-t / 0.015);
            if (i < attack) envelope *= double(i) / double(std::max<int64_t>(1, attack - 1));  // (sin chasquido)
            double tone = std::sin(2 * PI * pitch * t) + 0.35 * std::sin(2 * PI * 2 * pitch * t);
            float click = float(level / 1.35 * envelope * tone);
            for (int c = 0; c < channels; ++c) block.frames[size_t((start + i) * channels + c)] += click;
        }
    }
    block.start = 0;
    block.step = 0;
    block.count = true;
    block.lead = lead;
    block.beat = beat;
    block.beats = beats;
    return block;
}

// --- Renderer

Renderer::Renderer(std::shared_ptr<Audio> audio)
    : audio_(std::move(audio)), xfade_(std::max<int64_t>(16, int64_t(LOOP_FADE * audio_->rate))) {
    RubberBandStretcher::setDefaultDebugLevel(0);
}

Renderer::~Renderer() = default;

void Renderer::set_loop(std::optional<std::pair<int64_t, int64_t>> loop) { loop_ = loop; }

Block Renderer::empty(double start, double step) const {
    Block block;
    block.channels = audio_->channels;
    block.start = start;
    block.step = step;
    return block;
}

std::vector<float> Renderer::read(int64_t start, int64_t frames) {
    // FRAMES cuadros del archivo desde START, como se oyen: con una selección repetida se detienen en su final,
    // y los últimos momentos se funden con los que preceden a su principio, de modo que el final entra en el
    // principio sin chasquido ni hueco (quien llama sigue desde el principio).
    const int ch = audio_->channels;
    bool looping = loop_ && start < loop_->second;
    if (looping) frames = std::min(frames, loop_->second - start);
    frames = std::max<int64_t>(0, frames);
    std::vector<float> data(size_t(frames * ch));
    int64_t got = audio_->read(start, start + frames, data.data());
    data.resize(size_t(got * ch));
    if (looping && got) {
        auto [begin, end] = *loop_;
        int64_t x = xfade_;
        int64_t lo = std::max(start, end - x), hi = start + got;
        if (hi > lo) {
            // Fundido de igual potencia: el final se apaga mientras sube lo que precede al principio.
            int64_t first = lo - end + begin;  // (de begin - x a begin)
            int64_t n = hi - lo;
            std::vector<float> other(size_t(n * ch), 0.0f), got_other(size_t(n * ch));
            int64_t g = audio_->read(std::max<int64_t>(0, first), first + n, got_other.data());
            std::copy(got_other.begin(), got_other.begin() + g * ch, other.begin() + (n - g) * ch);
            for (int64_t f = lo; f < hi; ++f) {
                double k = (double(f - (end - x)) + 0.5) / double(x);
                float fade_in = float(std::sin(k * PI / 2)), fade_out = float(std::cos(k * PI / 2));
                for (int c = 0; c < ch; ++c) {
                    float& sample = data[size_t((f - start) * ch + c)];
                    sample = sample * fade_out + other[size_t((f - lo) * ch + c)] * fade_in;
                }
            }
        }
    }
    return data;
}

bool Renderer::wraps(double before, double after) const {
    return loop_ && before < double(loop_->second) && double(loop_->second) <= after + 0.5;
}

void Renderer::seek(double frame, bool fade) {
    pos_ = std::min(std::max(0.0, frame), double(std::max<int64_t>(0, audio_->length())));
    stretching_ = false;
    seam_ = fade;
}

void Renderer::set_speed(double speed) {
    speed = clamp_speed(speed);
    if (speed != speed_) {
        bool was_plain = plain();
        speed_ = speed;
        changed(was_plain);
    }
}

void Renderer::set_pitch(double semitones) {
    semitones = clamp_pitch(semitones);
    if (semitones != pitch_) {
        bool was_plain = plain();
        pitch_ = semitones;
        changed(was_plain);
    }
}

void Renderer::set_quality(int quality) {
    if (quality < QUALITY_HIGH || quality > QUALITY_R2) quality = QUALITY_HIGH;
    if (quality != quality_) {
        quality_ = quality;
        stretcher_.reset();  // (sus opciones se fijan al hacerlo)
        if (stretching_) {
            stretching_ = false;
            seam_ = true;
        }
    }
}

void Renderer::set_formants(bool preserved) {
    formants_ = preserved;
    if (stretcher_)
        stretcher_->setFormantOption(preserved ? RubberBandStretcher::OptionFormantPreserved
                                               : RubberBandStretcher::OptionFormantShifted);
}

void Renderer::changed(bool was_plain) {
    if (stretching_) {
        if (plain()) {
            // De Rubber Band a la copia tal cual, desde donde ha llegado la salida.
            stretching_ = false;
            seam_ = true;
        } else {
            stretcher_->setTimeRatio(1.0 / speed_);
            stretcher_->setPitchScale(pitch_scale(pitch_));
        }
    } else if (!was_plain) {
        seam_ = true;
    }
}

std::optional<Block> Renderer::render(int frames, bool& seam) {
    std::optional<Block> block = plain() ? copy(frames) : stretch(frames);
    seam = false;
    if (block && block->size()) {
        seam = seam_;
        seam_ = false;
        if (seam) {
            int64_t ramp = std::min<int64_t>(FADE, block->size());
            const int ch = block->channels;
            for (int64_t i = 0; i < ramp; ++i) {
                float gain = ramp > 1 ? float(i) / float(ramp - 1) : 0.0f;
                for (int c = 0; c < ch; ++c) block->frames[size_t(i * ch + c)] *= gain;
            }
        }
    }
    return block;
}

std::optional<Block> Renderer::copy(int frames) {
    int64_t start = std::llround(pos_);
    if (start >= audio_->decoded()) {
        if (audio_->done()) return std::nullopt;
        return empty(double(start), 1.0);
    }
    Block block = empty(double(start), 1.0);
    block.frames = read(start, frames);
    pos_ = double(start + block.size());
    if (wraps(double(start), pos_)) pos_ = double(loop_->first);
    return block;
}

void Renderer::process(const std::vector<float>& interleaved, bool final) {
    // De intercalado a un plano por canal, como lo pide Rubber Band
    const int ch = audio_->channels;
    int64_t n = int64_t(interleaved.size()) / ch;
    int64_t done = 0;
    do {
        int64_t take = std::min<int64_t>(MAX_PROCESS, n - done);
        planar_.resize(size_t(std::max<int64_t>(1, take) * ch));
        pointers_.resize(size_t(ch));
        for (int c = 0; c < ch; ++c) {
            pointers_[size_t(c)] = planar_.data() + c * take;
            for (int64_t i = 0; i < take; ++i)
                planar_[size_t(c * take + i)] = interleaved[size_t((done + i) * ch + c)];
        }
        done += take;
        stretcher_->process(pointers_.data(), size_t(take), final && done >= n);
    } while (done < n);
}

std::vector<float> Renderer::retrieve(int64_t frames) {
    const int ch = audio_->channels;
    planar_.resize(size_t(std::max<int64_t>(1, frames) * ch));
    pointers_.resize(size_t(ch));
    for (int c = 0; c < ch; ++c) pointers_[size_t(c)] = planar_.data() + c * frames;
    int64_t got = int64_t(stretcher_->retrieve(pointers_.data(), size_t(frames)));
    std::vector<float> out(size_t(got * ch));
    for (int64_t i = 0; i < got; ++i)
        for (int c = 0; c < ch; ++c) out[size_t(i * ch + c)] = planar_[size_t(c * frames + i)];
    return out;
}

void Renderer::restart() {
    // El estirador empieza en pos_: recibe antes el audio que precede a ese punto (o silencio, al principio
    // del archivo), y su salida se descarta hasta el retraso que anuncia; así lo que sale después corresponde
    // justo a pos_, sin el arranque en falso de un estirador vacío.
    const int ch = audio_->channels;
    if (!stretcher_) {
        stretcher_ = std::make_unique<RubberBandStretcher>(size_t(audio_->rate), size_t(ch), options(quality_),
                                                           1.0 / speed_, 1.0);
        stretcher_->setMaxProcessSize(MAX_PROCESS);
        set_formants(formants_);
    }
    RubberBandStretcher& st = *stretcher_;
    st.reset();
    st.setTimeRatio(1.0 / speed_);
    st.setPitchScale(pitch_scale(pitch_));
    int64_t start = std::llround(pos_);
    pos_ = double(start);
    int64_t pad = int64_t(st.getPreferredStartPad());
    std::vector<float> lead(size_t(pad * ch), 0.0f), before(size_t(pad * ch));
    int64_t got = audio_->read(start - pad, start, before.data());
    std::copy(before.begin(), before.begin() + got * ch, lead.begin() + (pad - got) * ch);
    if (pad) process(lead, false);
    discard_ = int64_t(st.getStartDelay());
    feed_ = start;
    final_ = false;
    stretching_ = true;
}

std::optional<Block> Renderer::stretch(int frames) {
    if (!stretching_) {
        if (pos_ >= double(audio_->decoded()) && audio_->done()) return std::nullopt;
        restart();
    }
    RubberBandStretcher& st = *stretcher_;
    while (st.available() < frames + discard_ && !final_) {
        int64_t need = std::min<int64_t>(MAX_PROCESS, std::max<int64_t>(int64_t(st.getSamplesRequired()), 256));
        std::vector<float> chunk = read(feed_, need);
        int64_t n = int64_t(chunk.size()) / audio_->channels;
        bool looping = loop_ && feed_ < loop_->second;
        bool last = !looping && audio_->done() && feed_ + n >= audio_->decoded();
        if (!n && !last) break;  // todavía se decodifica
        process(chunk, last);
        int64_t fed = feed_;
        feed_ += n;
        if (wraps(double(fed), double(feed_))) feed_ = loop_->first;  // (sin corte: recibe la selección una y otra vez)
        final_ = last;
    }
    int64_t available = st.available();
    if (available <= 0) {
        if (final_ && available < 0) return std::nullopt;
        return empty(pos_, speed_);
    }
    if (discard_) {
        int64_t drop = std::min(discard_, available);
        retrieve(drop);
        discard_ -= drop;
        available -= drop;
        if (available <= 0) return empty(pos_, speed_);
    }
    if (loop_ && pos_ < double(loop_->second)) {
        // Un bloque no pasa del final de la selección: el que sigue empieza en su principio.
        frames = int(std::max<int64_t>(1, std::min<int64_t>(frames, int64_t(std::ceil((double(loop_->second) - pos_) / speed_)))));
    }
    Block block = empty(pos_, speed_);
    block.frames = retrieve(std::min<int64_t>(frames, available));
    double before = pos_;
    pos_ = std::min(pos_ + double(block.size()) * speed_, double(audio_->decoded()));
    if (wraps(before, pos_)) pos_ = double(loop_->first);
    return block;
}

// --- Player

Player::Player(std::unique_ptr<Output> output) : output_(std::move(output)) {}

Player::~Player() { unload(); }

bool Player::load(std::shared_ptr<Audio> audio) {
    unload();
    audio_ = audio;
    renderer_ = std::make_unique<Renderer>(audio);
    renderer_->set_speed(speed_);
    renderer_->set_pitch(pitch_);
    renderer_->set_formants(formants_);
    renderer_->set_quality(quality_);
    hearing_ = std::make_unique<Hearing>(audio->rate);
    hearing_->set(hearing_channels_, hearing_filter_);
    equalizer_ = std::make_unique<Equalizer>(audio->rate);
    {
        std::lock_guard<std::mutex> guard(lock_);
        equalizer_->set(eq_gains_);
    }
    {
        std::lock_guard<std::mutex> guard(lock_);
        loop_.reset();
        position_ = 0;
        queue_.clear();
        queued_ = 0;
        eof_ = false;
        requests_.clear();
        running_ = true;
        sleeping_ = false;
        gain_ = 0;
    }
    ended_ = false;
    bool ok = output_ ? output_->open(audio->rate, audio->channels, [this](float* out, int frames) {
        callback(out, frames);
    }) : true;
    thread_ = std::thread([this] { produce(); });
    return ok;
}

void Player::unload() {
    playing_ = false;
    if (thread_.joinable()) {
        {
            std::lock_guard<std::mutex> guard(lock_);
            running_ = false;
            wake_.notify_all();
        }
        thread_.join();
    }
    if (output_) output_->close();
    renderer_.reset();
    audio_.reset();
}

void Player::request(int kind, double a, double b) {
    std::lock_guard<std::mutex> guard(lock_);
    requests_.push_back({kind, a, b});
    wake_.notify_all();
}

void Player::play(int beats, double bpm, double offset) {
    if (!audio_) return;
    bool restart;
    {
        std::lock_guard<std::mutex> guard(lock_);
        restart = ended_.load() || (eof_ && queue_.empty());
    }
    if (restart) seek(0.0);
    {
        std::lock_guard<std::mutex> guard(lock_);
        // Lo que quede de una cuenta anterior (una pausa en medio de ella) no se oye.
        for (auto it = queue_.begin(); it != queue_.end();) {
            if (it->count) {
                queued_ -= it->size() - it->used;
                it = queue_.erase(it);
            } else {
                ++it;
            }
        }
        if (beats > 0 && bpm > 0) {
            // (segundos de la grabación: a 0,5× se oyen el doble)
            Block block = count_in(audio_->rate, audio_->channels, beats, bpm, offset / speed_);
            block.start = position_;
            queued_ += block.size();
            queue_.push_front(std::move(block));
        }
        wake_.notify_all();
    }
    {
        std::lock_guard<std::mutex> control(control_);
        bool wake;
        {
            std::lock_guard<std::mutex> guard(lock_);
            wake = sleeping_;
            sleeping_ = false;
        }
        if (wake && output_) output_->resume();
    }
    ended_ = false;
    playing_ = true;
}

void Player::pause() { playing_ = false; }

void Player::stop() {
    playing_ = false;
    seek(0.0);
}

void Player::seek(double seconds) {
    if (!audio_) return;
    double frame = std::min(std::max(0.0, seconds * audio_->rate), double(audio_->length()));
    std::lock_guard<std::mutex> guard(lock_);
    // Lo que ya está en la cola se corta, salvo una rampa corta de bajada que se oye antes del salto.
    cut_queue(playing_ ? FADE : 0);
    requests_.push_back({SEEK, frame, 0});
    position_ = frame;
    eof_ = false;
    ended_ = false;
    wake_.notify_all();
}

void Player::set_loop(std::optional<std::pair<double, double>> loop) {
    if (!audio_) return;
    std::optional<std::pair<int64_t, int64_t>> frames;
    {
        std::lock_guard<std::mutex> guard(lock_);
        loop_.reset();
    }
    if (loop) {
        int rate = audio_->rate;
        double start = std::min(loop->first, loop->second), end = std::max(loop->first, loop->second);
        int64_t a = int64_t(std::max(0.0, start) * rate);
        int64_t b = std::min(int64_t(end * rate), audio_->decoded());
        if (b - a >= int64_t(MIN_LOOP * rate)) {
            frames = std::make_pair(a, b);
            std::lock_guard<std::mutex> guard(lock_);
            loop_ = std::make_pair(double(a) / rate, double(b) / rate);
        }
    }
    double here = position();
    if (frames) request(LOOP, double(frames->first), double(frames->second));
    else request(LOOP, -1, -1);
    // Lo que ya está en la cola se hizo sin la selección: se rehace desde donde suena (o desde su principio).
    bool inside = frames && double(frames->first) <= here * audio_->rate && here * audio_->rate < double(frames->second);
    seek(!frames || inside ? here : double(frames->first) / audio_->rate);
}

std::optional<std::pair<double, double>> Player::loop() const {
    std::lock_guard<std::mutex> guard(lock_);
    return loop_;
}

std::optional<std::pair<int, int>> Player::counting() {
    std::lock_guard<std::mutex> guard(lock_);
    if (!playing_ || queue_.empty() || !queue_.front().count) return std::nullopt;
    const Block& block = queue_.front();
    if (block.used < block.lead) return std::make_pair(0, int(block.beats));
    return std::make_pair(int(std::min<int64_t>(block.beats, (block.used - block.lead) / block.beat + 1)),
                          int(block.beats));
}

void Player::set_speed(double speed) {
    speed_ = clamp_speed(speed);
    request(SPEED, speed_);
}

void Player::set_pitch(double semitones) {
    pitch_ = clamp_pitch(semitones);
    request(PITCH, pitch_);
}

void Player::set_formants(bool preserved) {
    formants_ = preserved;
    request(FORMANTS, preserved ? 1 : 0);
}

void Player::set_hearing(int channels, int filter) {
    hearing_channels_ = channels;
    hearing_filter_ = filter;
    request(HEARING, channels, filter);
}

void Player::set_equalizer(const Gains& gains) {
    {
        std::lock_guard<std::mutex> guard(lock_);
        eq_gains_ = eq_clamp(gains);
    }
    request(EQUALIZER);
}

void Player::set_quality(int quality) {
    quality_ = (quality >= QUALITY_HIGH && quality <= QUALITY_R2) ? quality : QUALITY_HIGH;
    request(QUALITY, quality_);
}

double Player::position() {
    if (!audio_) return 0.0;
    double latency = output_ ? output_->latency() : 0.0;
    double frame;
    std::optional<std::pair<double, double>> loop;
    {
        std::lock_guard<std::mutex> guard(lock_);
        frame = position_;
        // Lo que salió de la cola se oye solo después de la latencia de la salida.
        if (playing_ && !queue_.empty()) frame -= latency * audio_->rate * queue_.front().step;
        loop = loop_;
    }
    double seconds = std::max(0.0, frame / audio_->rate);
    // (justo después del salto al principio de la selección, la latencia lo dejaría un poco antes)
    if (loop && loop->first - 0.25 < seconds && seconds < loop->first) seconds = loop->first;
    return seconds;
}

void Player::control(bool sleep) {
    std::lock_guard<std::mutex> control(control_);
    if (output_->lost() && audio_) {
        // Se cambió la salida (auriculares, Bluetooth): se abre otra vez
        output_->close();
        output_->open(audio_->rate, audio_->channels, [this](float* out, int frames) { callback(out, frames); });
        bool asleep;
        {
            std::lock_guard<std::mutex> guard(lock_);
            asleep = sleeping_;
        }
        if (asleep) output_->pause();
    }
    if (sleep) {
        std::lock_guard<std::mutex> guard(lock_);
        if (playing_ || sleeping_) return;  // (play llegó entretanto)
        sleeping_ = true;
    }
    if (sleep) output_->pause();
}

int64_t Player::target() const { return int64_t(QUEUE_SECONDS * (audio_ ? audio_->rate : 44100)); }

void Player::produce() {
    Renderer& renderer = *renderer_;
    auto idle_since = std::chrono::steady_clock::now();
    while (true) {
        std::vector<Request> requests;
        Gains gains{};
        {
            std::unique_lock<std::mutex> guard(lock_);
            while (running_ && (eof_ || queued_ >= target()) && requests_.empty()) {
                wake_.wait_for(guard, std::chrono::milliseconds(50));
                // Sin reproducir y ya en silencio: la salida duerme (no gasta batería ni retiene el sonido).
                // La salida se maneja sin el cerrojo, que su función también toma.
                auto now = std::chrono::steady_clock::now();
                bool sleep = false;
                if (playing_ || gain_ != 0) idle_since = now;
                else if (!sleeping_ && now - idle_since > std::chrono::milliseconds(500)) sleep = true;
                if (output_ && (sleep || output_->lost())) {
                    guard.unlock();
                    control(sleep);
                    guard.lock();
                }
            }
            if (!running_) return;
            requests.swap(requests_);
            gains = eq_gains_;
        }
        for (const Request& r : requests) {
            switch (r.kind) {
                case SEEK: renderer.seek(r.a); break;
                case SPEED: renderer.set_speed(r.a); break;
                case LOOP:
                    if (r.a < 0) renderer.set_loop(std::nullopt);
                    else renderer.set_loop(std::make_pair(int64_t(r.a), int64_t(r.b)));
                    break;
                case PITCH: renderer.set_pitch(r.a); break;
                case FORMANTS: renderer.set_formants(r.a != 0); break;
                case QUALITY: renderer.set_quality(int(r.a)); break;
                case HEARING: if (hearing_) hearing_->set(int(r.a), int(r.b)); break;
                case EQUALIZER: if (equalizer_) equalizer_->set(gains); break;
            }
        }
        bool seam = false;
        std::optional<Block> block = renderer.render(BLOCK, seam);
        if (block && block->size() && hearing_) {
            if (seam) hearing_->reset();  // (lo de antes del corte no se mezcla con lo que sigue)
            hearing_->process(block->frames, block->channels);
        }
        if (block && block->size() && equalizer_) {
            if (seam) equalizer_->reset();
            equalizer_->process(block->frames, block->channels);
        }
        std::unique_lock<std::mutex> guard(lock_);
        bool seek_pending = std::any_of(requests_.begin(), requests_.end(), [](const Request& r) { return r.kind == SEEK; });
        if (seek_pending) continue;  // un salto llegó mientras se hacía este bloque: no se oye
        if (!block) {
            eof_ = true;
            continue;
        }
        if (!block->size()) {
            wake_.wait_for(guard, std::chrono::milliseconds(20));  // waiting for the decoder
            continue;
        }
        if (seam) fade_tail();
        queued_ += block->size();
        queue_.push_back(std::move(*block));
    }
}

void Player::cut_queue(int64_t keep) {
    // Deja solo los primeros KEEP cuadros por tocar, que bajan a cero (pueden sonar antes de que el productor
    // alcance a poner el bloque que sigue al corte).
    std::deque<Block> kept;
    int64_t left = keep;
    for (const Block& block : queue_) {
        if (left <= 0) break;
        int64_t rest = block.size() - block.used;
        int64_t take = std::min(left, rest);
        Block part;
        part.channels = block.channels;
        part.start = block.position();
        part.step = block.step;
        part.frames.assign(block.frames.begin() + block.used * block.channels,
                           block.frames.begin() + (block.used + take) * block.channels);
        kept.push_back(std::move(part));
        left -= take;
    }
    queue_.swap(kept);
    queued_ = keep - std::max<int64_t>(0, left);
    fade_tail();
}

void Player::fade_tail() {
    // Una rampa de bajada en los últimos FADE cuadros que hay en la cola, antes de un corte: el último cuadro
    // queda en cero.
    int64_t total = std::min<int64_t>(FADE, queued_);
    int64_t done = 0;  // cuadros ya rebajados, contados desde el final
    for (auto it = queue_.rbegin(); it != queue_.rend() && done < total; ++it) {
        Block& block = *it;
        int64_t rest = block.size() - block.used;
        int64_t take = std::min(total - done, rest);
        for (int64_t j = 0; j < take; ++j) {
            // j = 0 es el último cuadro del bloque
            float gain = float(done + j) / float(total);
            int64_t frame = block.size() - 1 - j;
            for (int c = 0; c < block.channels; ++c) block.frames[size_t(frame * block.channels + c)] *= gain;
        }
        done += take;
    }
}

void Player::callback(float* out, int frames) {
    const int ch = audio_ ? audio_->channels : 1;
    std::fill(out, out + size_t(frames) * ch, 0.0f);
    float target_gain = playing_ ? 1.0f : 0.0f;
    std::lock_guard<std::mutex> guard(lock_);
    if (gain_ == 0.0f && target_gain == 0.0f) return;
    int64_t filled = 0;
    while (filled < frames && !queue_.empty()) {
        Block& block = queue_.front();
        int64_t take = std::min<int64_t>(frames - filled, block.size() - block.used);
        std::copy(block.frames.begin() + block.used * ch, block.frames.begin() + (block.used + take) * ch,
                  out + filled * ch);
        block.used += take;
        filled += take;
        queued_ -= take;
        position_ = block.position();
        if (block.used >= block.size()) queue_.pop_front();
    }
    if (queue_.empty() && eof_ && playing_) {
        playing_ = false;
        ended_ = true;
        position_ = audio_ ? double(audio_->decoded()) : 0.0;
    }
    wake_.notify_all();
    // The pause ramp: from the current gain to the new one, along this block.
    if (gain_ != target_gain) {
        for (int i = 0; i < frames; ++i) {
            float g = gain_ + (target_gain - gain_) * (frames > 1 ? float(i) / float(frames - 1) : 1.0f);
            for (int c = 0; c < ch; ++c) out[i * ch + c] *= g;
        }
        gain_ = target_gain;
    }
    float volume = volume_.load();
    if (volume != 1.0f) {
        for (int i = 0; i < frames * ch; ++i) {
            float v = out[i] * volume;
            // (más alto que la grabación: lo que pase del máximo se recorta)
            out[i] = volume > 1.0f ? std::min(1.0f, std::max(-1.0f, v)) : v;
        }
    }
}

}  // namespace slowdown
