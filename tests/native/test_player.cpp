// SPDX-License-Identifier: GPL-3.0-or-later
// Player con una salida de prueba: la cola, la posición, la pausa, el salto, el final y el bucle.
#include <thread>

#include "check.h"
#include "engine.h"

using namespace slowdown;

namespace {

// Una salida que no suena: la prueba llama a la función de la salida cuando quiere.
class FakeOutput : public Output {
public:
    bool open(int, int, Callback) override { return true; }
    void close() override {}
    double latency() override { return 0.0; }
    void pause() override { ++pauses; }
    void resume() override { ++resumes; }
    int pauses = 0, resumes = 0;
};

// Pide a la salida FRAMES cuadros, en bloques de 256, dejando al productor tiempo de llenar la cola.
std::vector<float> pull(Player& p, int64_t frames, int channels) {
    std::vector<float> out;
    std::vector<float> buffer(256 * size_t(channels));
    for (int64_t done = 0; done < frames; done += 256) {
        // (unas cinco veces más rápido que el tiempo real: el productor alcanza a llenar la cola)
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
        p.callback(buffer.data(), 256);
        out.insert(out.end(), buffer.begin(), buffer.end());
    }
    return out;
}

void settle() { std::this_thread::sleep_for(std::chrono::milliseconds(150)); }

}  // namespace

int main() {
    auto audio = tone(3.0);
    auto output = std::make_unique<FakeOutput>();
    FakeOutput* fake = output.get();
    Player p(std::move(output));
    CHECK(p.load(audio));
    settle();
    // En pausa no sale nada ni se mueve la posición
    pull(p, 4800, 2);
    CHECK(p.position() == 0.0);
    // Al rato, la salida duerme
    std::this_thread::sleep_for(std::chrono::milliseconds(700));
    CHECK(fake->pauses == 1);

    p.play();
    CHECK(fake->resumes == 1);
    settle();
    auto out = pull(p, 48000, 2);
    CHECK_NEAR(p.position(), 1.0, 0.01);
    float loudest = 0;
    for (float v : out) loudest = std::max(loudest, std::fabs(v));
    CHECK(loudest > 0.4f);

    // Un salto: la posición va allí enseguida
    p.seek(2.0);
    CHECK_NEAR(p.position(), 2.0, 1e-9);
    settle();
    pull(p, 24000, 2);
    CHECK_NEAR(p.position(), 2.5, 0.02);

    // El final: se detiene, y lo dice una vez
    pull(p, 48000, 2);
    CHECK(!p.playing());
    CHECK(p.take_ended());
    CHECK(!p.take_ended());
    CHECK_NEAR(p.position(), 3.0, 1e-6);

    // Otra vez desde el principio, más lento
    p.set_speed(0.5);
    p.play();
    settle();
    pull(p, 48000, 2);
    CHECK_NEAR(p.position(), 0.5, 0.03);

    // El bucle: no sale de la selección
    p.set_loop(std::make_pair(1.0, 1.5));
    CHECK(p.loop().has_value());
    settle();
    for (int i = 0; i < 20; ++i) {
        pull(p, 4800, 2);
        double here = p.position();
        CHECK(here >= 1.0 - 1e-6 && here < 1.5 + 0.01);
    }
    // Una selección muy corta no se repite
    p.set_loop(std::make_pair(1.0, 1.05));
    CHECK(!p.loop().has_value());

    // La cuenta previa: la posición se queda quieta mientras suenan los golpes
    p.pause();
    pull(p, 1024, 2);
    p.set_speed(1.0);
    p.seek(1.0);
    p.play(2, 120);
    settle();
    pull(p, 4800, 2);
    auto counting = p.counting();
    CHECK(counting.has_value() && counting->second == 2);
    CHECK_NEAR(p.position(), 1.0, 1e-6);
    pull(p, 48000 + 24000, 2);
    CHECK(!p.counting().has_value());
    CHECK(p.position() > 1.0);

    p.unload();
    return finish();
}
