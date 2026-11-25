// SPDX-License-Identifier: GPL-3.0-or-later
// Cuántas veces el tiempo real alcanza el estirador, en cada calidad: para correrlo en el teléfono
// (tools/bench-device.sh) o en el ordenador.
#include <chrono>
#include <cstdio>

#include "check.h"
#include "engine.h"

using namespace slowdown;

int main() {
    const double seconds = 20.0;
    auto audio = tone(seconds, 48000, 2);
    const char* names[] = {"high (R3)", "light (R3 short)", "r2"};
    for (int quality : {QUALITY_HIGH, QUALITY_LIGHT, QUALITY_R2}) {
        for (double speed : {0.5, 0.75, 1.25}) {
            Renderer r(audio);
            r.set_quality(quality);
            r.set_speed(speed);
            bool seam;
            int64_t frames = 0;
            auto t0 = std::chrono::steady_clock::now();
            while (auto block = r.render(BLOCK, seam)) frames += block->size();
            double took = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
            double heard = double(frames) / audio->rate;
            std::printf("%-18s %.2fx: %5.1f times real time\n", names[quality], speed, heard / took);
        }
    }
    return 0;
}
