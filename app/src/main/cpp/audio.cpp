// SPDX-License-Identifier: GPL-3.0-or-later
#include "audio.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace slowdown {

Audio::Audio(int rate_, int channels_, int64_t expected)
    : rate(rate_), channels(channels_), chunks_(new std::atomic<Chunk*>[MAX_CHUNKS]) {
    for (int64_t i = 0; i < MAX_CHUNKS; ++i) chunks_[i].store(nullptr, std::memory_order_relaxed);
    expected_.store(std::max<int64_t>(0, expected));
}

Audio::~Audio() {
    for (int64_t i = 0; i < MAX_CHUNKS; ++i) delete chunks_[i].load();
}

int64_t Audio::length() const {
    int64_t got = decoded();
    if (done()) return got;
    return std::max(got, expected_.load());
}

void Audio::append(const float* frames, int64_t count) {
    int64_t at_ = decoded_.load(std::memory_order_relaxed);
    while (count > 0) {
        int64_t index = at_ >> CHUNK_SHIFT;
        if (index >= MAX_CHUNKS) return;  // (más largo de lo que cabe: se queda lo que entró)
        Chunk* chunk = chunks_[index].load(std::memory_order_relaxed);
        if (chunk == nullptr) {
            chunk = new Chunk();
            chunk->frames.reset(new float[CHUNK * channels]);
            chunks_[index].store(chunk, std::memory_order_release);
        }
        int64_t offset = at_ & (CHUNK - 1);
        int64_t take = std::min(count, CHUNK - offset);
        std::memcpy(chunk->frames.get() + offset * channels, frames, sizeof(float) * take * channels);
        // Los picos de la onda, de la mezcla de los canales
        for (int64_t i = 0; i < take; ++i) {
            int64_t frame = offset + i;
            float sum = 0;
            for (int c = 0; c < channels; ++c) sum += frames[i * channels + c];
            sum /= float(channels);
            if (frame % PEAK == 0) {
                pending_lo_ = pending_hi_ = sum;
            } else {
                pending_lo_ = std::min(pending_lo_, sum);
                pending_hi_ = std::max(pending_hi_, sum);
            }
            chunk->lo[frame / PEAK] = pending_lo_;
            chunk->hi[frame / PEAK] = pending_hi_;
        }
        frames += take * channels;
        count -= take;
        at_ += take;
        decoded_.store(at_, std::memory_order_release);
    }
}

void Audio::finish(bool failed) {
    failed_.store(failed, std::memory_order_release);
    done_.store(true, std::memory_order_release);
}

const float* Audio::at(int64_t frame) const {
    Chunk* chunk = chunks_[frame >> CHUNK_SHIFT].load(std::memory_order_acquire);
    return chunk->frames.get() + (frame & (CHUNK - 1)) * channels;
}

int64_t Audio::read(int64_t start, int64_t end, float* out) const {
    start = std::max<int64_t>(0, start);
    end = std::min(end, decoded());
    int64_t done_frames = 0;
    for (int64_t frame = start; frame < end;) {
        int64_t take = std::min(end - frame, CHUNK - (frame & (CHUNK - 1)));
        std::memcpy(out + done_frames * channels, at(frame), sizeof(float) * take * channels);
        frame += take;
        done_frames += take;
    }
    return done_frames;
}

void Audio::peaks(double start, double fpp, int count, float* out) const {
    int64_t have = decoded();
    for (int i = 0; i < count; ++i) {
        int64_t a = int64_t(std::floor(start + i * fpp));
        int64_t b = std::max(a + 1, int64_t(std::floor(start + (i + 1) * fpp)));
        a = std::max<int64_t>(0, a);
        b = std::min(b, have);
        float lo = 0, hi = 0;
        if (a < b) {
            lo = INFINITY;
            hi = -INFINITY;
            if (fpp >= PEAK) {
                // Con los picos ya hechos: un bloque de PEAK cuadros a la vez
                for (int64_t block = a / PEAK; block * PEAK < b; ++block) {
                    Chunk* chunk = chunks_[(block * PEAK) >> CHUNK_SHIFT].load(std::memory_order_acquire);
                    int64_t k = (block * PEAK & (CHUNK - 1)) / PEAK;
                    lo = std::min(lo, chunk->lo[k]);
                    hi = std::max(hi, chunk->hi[k]);
                }
            } else {
                for (int64_t f = a; f < b; ++f) {
                    const float* frame = at(f);
                    float sum = 0;
                    for (int c = 0; c < channels; ++c) sum += frame[c];
                    sum /= float(channels);
                    lo = std::min(lo, sum);
                    hi = std::max(hi, sum);
                }
            }
        }
        out[2 * i] = lo;
        out[2 * i + 1] = hi;
    }
}

}  // namespace slowdown
