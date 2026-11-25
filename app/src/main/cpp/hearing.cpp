// SPDX-License-Identifier: GPL-3.0-or-later
#include "hearing.h"

#include <algorithm>
#include <cmath>

#include "src/common/FFT.h"

namespace slowdown {

namespace {
constexpr double PI = 3.14159265358979323846;
}

std::vector<float> kernel(int filter, int rate) {
    int size = int(KERNEL_SECONDS * rate) | 1;  // (impar: el retraso es un número entero de cuadros)
    int middle = size / 2;
    double cutoff = CROSSOVER / rate;
    // Una sinc con ventana de Blackman: unos 60 dB de rechazo, una transición de unos 100 Hz
    std::vector<double> low(static_cast<size_t>(size));
    double sum = 0;
    for (int i = 0; i < size; ++i) {
        double n = i - middle;
        double x = 2 * cutoff * n;
        double sinc = n == 0 ? 1.0 : std::sin(PI * x) / (PI * x);
        double window = 0.42 - 0.5 * std::cos(2 * PI * i / (size - 1)) + 0.08 * std::cos(4 * PI * i / (size - 1));
        low[size_t(i)] = 2 * cutoff * sinc * window;
        sum += low[size_t(i)];
    }
    std::vector<float> out(static_cast<size_t>(size));
    for (int i = 0; i < size; ++i) {
        double v = low[size_t(i)] / sum;
        out[size_t(i)] = float(filter == BASS ? v : -v);
    }
    if (filter != BASS) out[size_t(middle)] += 1.0f;  // (lo que queda del sonido entero sin los graves)
    return out;
}

struct Hearing::Spectrum {
    std::unique_ptr<RubberBand::FFT> fft;
    std::vector<float> re, im;  // el del núcleo
    std::vector<float> in, out, sre, sim;  // memoria de trabajo
};

Hearing::Hearing(int rate) : rate_(rate), size_(int(KERNEL_SECONDS * rate) | 1) {}

Hearing::~Hearing() = default;

void Hearing::set(int channels, int filter) {
    channels = (channels >= BOTH && channels <= NO_CENTRE) ? channels : BOTH;
    filter = (filter >= NONE && filter <= NO_BASS) ? filter : NONE;
    if (channels != channels_ || filter != filter_) {
        before_channels_ = channels_;
        before_filter_ = filter_;
        fading_ = true;
        channels_ = channels;
        filter_ = filter;
    }
}

void Hearing::reset() { history_.clear(); }

void Hearing::process(std::vector<float>& frames, int ch) {
    if (frames.empty()) return;
    int count = int(frames.size()) / ch;
    if (history_channels_ != ch || history_.empty()) {
        history_.assign(size_t(size_ - 1) * ch, 0.0f);
        history_channels_ = ch;
    }
    std::vector<float> whole(history_);
    whole.insert(whole.end(), frames.begin(), frames.end());
    history_.assign(whole.end() - (size_ - 1) * ch, whole.end());
    if (plain() && !fading_) return;
    std::vector<float> out = apply(whole, count, ch, channels_, filter_);
    if (fading_) {
        // Del modo de antes al nuevo, en un fundido a lo largo del bloque.
        std::vector<float> old = apply(whole, count, ch, before_channels_, before_filter_);
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

std::vector<float> Hearing::apply(const std::vector<float>& whole, int count, int ch, int channels, int filter) {
    int length = int(whole.size()) / ch;
    std::vector<float> filtered(size_t(count) * ch);
    if (filter == NONE) {
        std::copy(whole.end() - count * ch, whole.end(), filtered.begin());
    } else {
        int size = 1;
        while (size < length) size <<= 1;
        auto& spectrum = spectra_[{filter, size}];
        if (!spectrum) {
            spectrum = std::make_unique<Spectrum>();
            spectrum->fft = std::make_unique<RubberBand::FFT>(size);
            spectrum->fft->initFloat();
            std::vector<float> taps = kernel(filter, rate_);
            spectrum->in.assign(size_t(size), 0.0f);
            std::copy(taps.begin(), taps.end(), spectrum->in.begin());
            spectrum->re.resize(size_t(size / 2 + 1));
            spectrum->im.resize(size_t(size / 2 + 1));
            spectrum->fft->forward(spectrum->in.data(), spectrum->re.data(), spectrum->im.data());
            spectrum->out.resize(size_t(size));
            spectrum->sre.resize(size_t(size / 2 + 1));
            spectrum->sim.resize(size_t(size / 2 + 1));
        }
        Spectrum& s = *spectrum;
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
            for (int i = 0; i < count; ++i) filtered[size_t(i * ch + c)] = s.out[size_t(length - count + i)] / float(size);
        }
    }
    // Los canales: con un solo canal, tal cual
    if (ch < 2 || channels == BOTH) return filtered;
    for (int i = 0; i < count; ++i) {
        float left = filtered[size_t(i * ch)], right = filtered[size_t(i * ch + 1)];
        float one;
        switch (channels) {
            case LEFT: one = left; break;
            case RIGHT: one = right; break;
            case MONO: one = (left + right) * 0.5f; break;
            // Sin el centro: lo que está igual en los dos canales se anula (a 0,7: la diferencia puede pasar de 1)
            default: one = (left - right) * 0.7f; break;
        }
        for (int c = 0; c < ch; ++c) filtered[size_t(i * ch + c)] = one;
    }
    return filtered;
}

}  // namespace slowdown
