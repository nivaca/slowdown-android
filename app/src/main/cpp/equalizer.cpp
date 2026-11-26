// SPDX-License-Identifier: GPL-3.0-or-later
#include "equalizer.h"

#include <algorithm>
#include <cmath>

#include "src/common/FFT.h"

namespace slowdown {

namespace {
constexpr double PI = 3.14159265358979323846;
}

Gains eq_clamp(const Gains& gains) {
    Gains out{};
    for (int i = 0; i < EQ_BANDS; ++i) out[size_t(i)] = std::max(-EQ_MAX_GAIN, std::min(EQ_MAX_GAIN, gains[size_t(i)]));
    return out;
}

bool eq_flat(const Gains& gains) {
    return std::all_of(gains.begin(), gains.end(), [](int g) { return g == 0; });
}

int eq_size(int rate) { return int(EQ_KERNEL_SECONDS * rate) | 1; }

int eq_delay(const Gains& gains, int rate) { return eq_flat(gains) ? 0 : eq_size(rate) / 2; }

double eq_response(const Gains& gains, double freq) {
    // Como numpy.interp sobre log2 de las frecuencias: fuera de las bandas, la altura de la de la punta
    double where = std::log2(std::max(freq, 1.0));
    double first = std::log2(double(EQ_FREQUENCIES[0]));
    if (where <= first) return gains[0];
    for (int i = 1; i < EQ_BANDS; ++i) {
        double a = std::log2(double(EQ_FREQUENCIES[i - 1])), b = std::log2(double(EQ_FREQUENCIES[i]));
        if (where <= b) {
            double r = (where - a) / (b - a);
            return gains[size_t(i - 1)] + r * (gains[size_t(i)] - gains[size_t(i - 1)]);
        }
    }
    return gains[EQ_BANDS - 1];
}

std::vector<float> eq_kernel(const Gains& gains, int rate) {
    int taps = eq_size(rate);
    int points = 1;
    while (points < taps * 4) points <<= 1;
    // La respuesta pedida, de fase cero, de la frecuencia al tiempo
    std::vector<float> re(static_cast<size_t>(points / 2 + 1)), im(static_cast<size_t>(points / 2 + 1), 0.0f);
    std::vector<float> impulse(static_cast<size_t>(points));
    for (int k = 0; k <= points / 2; ++k) {
        double freq = double(k) * rate / points;
        re[size_t(k)] = float(std::pow(10.0, eq_response(gains, freq) / 20.0));
    }
    RubberBand::FFT fft(points);
    fft.initFloat();
    fft.inverse(re.data(), im.data(), impulse.data());
    // Centrada (simétrica alrededor de su medio) y con la ventana de Hann, como numpy.hanning
    std::vector<float> out(static_cast<size_t>(taps));
    int half = taps / 2;
    for (int i = 0; i < taps; ++i) {
        int from = ((i - half) % points + points) % points;
        double window = 0.5 - 0.5 * std::cos(2 * PI * i / (taps - 1));
        out[size_t(i)] = float(impulse[size_t(from)] / points * window);
    }
    return out;
}

struct Equalizer::Spectrum {
    std::unique_ptr<RubberBand::FFT> fft;
    std::vector<float> re, im;
    std::vector<float> in, out, sre, sim;
};

Equalizer::Equalizer(int rate) : rate_(rate), size_(eq_size(rate)) {}

Equalizer::~Equalizer() = default;

void Equalizer::set(const Gains& gains) {
    Gains clamped = eq_clamp(gains);
    if (clamped != gains_) {
        before_ = gains_;
        fading_ = true;
        gains_ = clamped;
    }
}

void Equalizer::reset() { history_.clear(); }

void Equalizer::process(std::vector<float>& frames, int ch) {
    if (frames.empty()) return;
    int count = int(frames.size()) / ch;
    if (history_channels_ != ch || history_.empty()) {
        history_.assign(size_t(size_ - 1) * ch, 0.0f);
        history_channels_ = ch;
    }
    std::vector<float> whole(history_);
    whole.insert(whole.end(), frames.begin(), frames.end());
    history_.assign(whole.end() - (size_ - 1) * ch, whole.end());
    if (eq_flat(gains_) && (!fading_ || eq_flat(before_))) {
        fading_ = false;
        return;
    }
    std::vector<float> out = apply(whole, count, ch, gains_);
    if (fading_) {
        std::vector<float> old = apply(whole, count, ch, before_);
        fading_ = false;
        for (int i = 0; i < count; ++i) {
            float r = count > 1 ? float(i) / float(count - 1) : 1.0f;
            for (int c = 0; c < ch; ++c) {
                size_t k = size_t(i * ch + c);
                out[k] = old[k] * (1.0f - r) + out[k] * r;
            }
        }
    }
    frames.swap(out);
}

std::vector<float> Equalizer::apply(const std::vector<float>& whole, int count, int ch, const Gains& gains) {
    std::vector<float> result(size_t(count) * ch);
    if (eq_flat(gains)) {
        std::copy(whole.end() - count * ch, whole.end(), result.begin());
        return result;
    }
    int length = int(whole.size()) / ch;
    int size = 1;
    while (size < length) size <<= 1;
    auto key = std::make_pair(gains, size);
    auto found = spectra_.find(key);
    if (found == spectra_.end()) {
        if (spectra_.size() > 32) spectra_.clear();  // (al girar una perilla se juntan muchos: los viejos ya no sirven)
        auto spectrum = std::make_unique<Spectrum>();
        spectrum->fft = std::make_unique<RubberBand::FFT>(size);
        spectrum->fft->initFloat();
        std::vector<float> taps = eq_kernel(gains, rate_);
        spectrum->in.assign(size_t(size), 0.0f);
        std::copy(taps.begin(), taps.end(), spectrum->in.begin());
        spectrum->re.resize(size_t(size / 2 + 1));
        spectrum->im.resize(size_t(size / 2 + 1));
        spectrum->fft->forward(spectrum->in.data(), spectrum->re.data(), spectrum->im.data());
        spectrum->out.resize(size_t(size));
        spectrum->sre.resize(size_t(size / 2 + 1));
        spectrum->sim.resize(size_t(size / 2 + 1));
        found = spectra_.emplace(key, std::move(spectrum)).first;
    }
    Spectrum& s = *found->second;
    for (int c = 0; c < ch; ++c) {
        std::fill(s.in.begin(), s.in.end(), 0.0f);
        for (int i = 0; i < length; ++i) s.in[size_t(i)] = whole[size_t(i * ch + c)];
        s.fft->forward(s.in.data(), s.sre.data(), s.sim.data());
        for (int k = 0; k <= size / 2; ++k) {
            float a = s.sre[size_t(k)], b = s.sim[size_t(k)];
            s.sre[size_t(k)] = a * s.re[size_t(k)] - b * s.im[size_t(k)];
            s.sim[size_t(k)] = a * s.im[size_t(k)] + b * s.re[size_t(k)];
        }
        s.fft->inverse(s.sre.data(), s.sim.data(), s.out.data());
        // (la convolución circular solo es buena desde el cuadro size_ - 1: lo que sigue a la historia)
        for (int i = 0; i < count; ++i) result[size_t(i * ch + c)] = s.out[size_t(length - count + i)] / float(size);
    }
    return result;
}

}  // namespace slowdown
