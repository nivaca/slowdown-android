// SPDX-License-Identifier: GPL-3.0-or-later
// Renderer: la copia tal cual a 1×, el estiramiento, el bucle sin hueco y los saltos.
#include "check.h"
#include "engine.h"

using namespace slowdown;

static std::vector<float> render_all(Renderer& r, int channels, int64_t limit = 1 << 26) {
    std::vector<float> out;
    bool seam;
    while (int64_t(out.size()) / channels < limit) {
        auto block = r.render(BLOCK, seam);
        if (!block) break;
        out.insert(out.end(), block->frames.begin(), block->frames.end());
    }
    return out;
}

int main() {
    // A 1× sale el archivo bit a bit, salvo la rampa del primer bloque
    {
        auto audio = tone(2.0);
        Renderer r(audio);
        r.seek(0, false);
        auto out = render_all(r, 2);
        CHECK(int64_t(out.size()) == audio->decoded() * 2);
        std::vector<float> original(out.size());
        audio->read(0, audio->decoded(), original.data());
        CHECK(out == original);
    }
    // A 0,5× dura el doble; a 2×, la mitad (más o menos lo que retiene Rubber Band al final)
    for (double speed : {0.5, 2.0, 0.75}) {
        for (int quality : {QUALITY_HIGH, QUALITY_LIGHT, QUALITY_R2}) {
            auto audio = tone(3.0);
            Renderer r(audio);
            r.set_quality(quality);
            r.set_speed(speed);
            auto out = render_all(r, 2);
            double seconds = double(out.size()) / 2 / audio->rate;
            CHECK_NEAR(seconds, 3.0 / speed, 0.2);
            // El tono sigue en 440 Hz: cuenta los cruces por cero de la izquierda, en el medio
            int64_t mid = int64_t(out.size()) / 4, span = audio->rate / 2;  // (medio segundo)
            int crossings = 0;
            for (int64_t i = mid; i < mid + span; ++i)
                crossings += (out[size_t(2 * i)] < 0) != (out[size_t(2 * i + 2)] < 0);
            CHECK_NEAR(crossings, 440.0, 12.0);  // (un semitono sería 466)
        }
    }
    // Un semitono arriba, a 1×: estirado, y el tono sube
    {
        auto audio = tone(2.0);
        Renderer r(audio);
        r.set_pitch(12);
        CHECK(!r.plain());
        auto out = render_all(r, 2);
        int64_t mid = int64_t(out.size()) / 4;
        int crossings = 0;
        for (int64_t i = mid; i < mid + audio->rate / 2; ++i)
            crossings += (out[size_t(2 * i)] < 0) != (out[size_t(2 * i + 2)] < 0);
        CHECK_NEAR(crossings, 880.0, 8.0);
    }
    // El bucle: tras el final de la selección vuelve a su principio, sin hueco, a 1× y estirado
    for (double speed : {1.0, 0.6}) {
        auto audio = tone(4.0);
        Renderer r(audio);
        r.set_speed(speed);
        int rate = audio->rate;
        r.set_loop(std::make_pair(int64_t(1 * rate), int64_t(2 * rate)));
        r.seek(1.5 * rate, false);
        bool seam;
        double last = 0;
        int wraps = 0;
        int64_t frames = 0;
        while (frames < int64_t(3.5 * rate / speed)) {
            auto block = r.render(BLOCK, seam);
            CHECK(block.has_value());
            if (!block) break;
            if (!block->size()) continue;
            CHECK(block->start >= rate - 1 && block->start < 2 * rate);
            if (block->start < last) ++wraps;
            last = block->start;
            frames += block->size();
        }
        CHECK(wraps >= 3);
    }
    // Un salto con rampa: el bloque siguiente empieza en silencio y avisa del corte
    {
        auto audio = tone(2.0);
        Renderer r(audio);
        bool seam;
        r.render(BLOCK, seam);
        r.seek(48000.0);
        auto block = r.render(BLOCK, seam);
        CHECK(seam);
        CHECK(block->start == 48000.0);
        CHECK(block->frames[0] == 0.0f);
    }
    // Mientras se decodifica: bloques vacíos, no el final
    {
        auto audio = std::make_shared<Audio>(48000, 2, 96000);
        Renderer r(audio);
        bool seam;
        auto block = r.render(BLOCK, seam);
        CHECK(block.has_value() && block->size() == 0);
        r.set_speed(0.5);
        block = r.render(BLOCK, seam);
        CHECK(block.has_value() && block->size() == 0);
        audio->finish();
        r.set_speed(1.0);
        r.seek(0);
        CHECK(!r.render(BLOCK, seam).has_value());
    }
    // La cuenta previa: pulsos, el primero más alto
    {
        Block c = count_in(48000, 2, 4, 120);
        CHECK(c.count && c.beats == 4 && c.beat == 24000);
        CHECK(c.size() == c.lead + 4 * 24000);
        CHECK(c.step == 0);
        CHECK(max_earlier(48000, 120) > 0.4);
    }
    CHECK_NEAR(clamp_speed(5), MAX_SPEED, 0);
    CHECK_NEAR(clamp_pitch(-20), -MAX_PITCH, 0);
    CHECK_NEAR(pitch_scale(12), 2.0, 1e-12);
    return finish();
}
