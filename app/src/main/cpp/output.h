// SPDX-License-Identifier: GPL-3.0-or-later
// La salida de sonido de Android: un flujo de AAudio que pide cuadros a Player.
#pragma once

#include <aaudio/AAudio.h>

#include <atomic>
#include <mutex>

#include "engine.h"

namespace slowdown {

class AAudioOutput : public Output {
public:
    ~AAudioOutput() override { close(); }
    bool open(int rate, int channels, Callback callback) override;
    void close() override;
    double latency() override;
    void pause() override;
    void resume() override;
    bool lost() override { return lost_.load(); }

private:
    std::mutex mutex_;
    AAudioStream* stream_ = nullptr;
    Callback callback_;
    int rate_ = 48000;
    std::atomic<bool> lost_{false};

    static aaudio_data_callback_result_t data(AAudioStream*, void* self, void* audio, int32_t frames);
    static void error(AAudioStream*, void* self, aaudio_result_t error);
};

}  // namespace slowdown
