// SPDX-License-Identifier: GPL-3.0-or-later
#include "output.h"

#include <android/log.h>

#include <ctime>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "slowdown", __VA_ARGS__)

namespace slowdown {

bool AAudioOutput::open(int rate, int channels, Callback callback) {
    std::lock_guard<std::mutex> guard(mutex_);
    callback_ = std::move(callback);
    rate_ = rate;
    lost_ = false;
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setSampleRate(builder, rate);  // (si la salida no la tiene, Android la convierte)
    AAudioStreamBuilder_setChannelCount(builder, channels);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(builder, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(builder, data, this);
    AAudioStreamBuilder_setErrorCallback(builder, error, this);
    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        LOG("AAudio: %s", AAudio_convertResultToText(result));
        stream_ = nullptr;
        return false;
    }
    LOG("AAudio: %d Hz, %d channels, buffer %d frames", AAudioStream_getSampleRate(stream_),
        AAudioStream_getChannelCount(stream_), AAudioStream_getBufferSizeInFrames(stream_));
    AAudioStream_requestStart(stream_);
    return true;
}

void AAudioOutput::close() {
    std::lock_guard<std::mutex> guard(mutex_);
    if (stream_) {
        AAudioStream_requestStop(stream_);
        AAudioStream_close(stream_);  // (espera a que termine la función de datos)
        stream_ = nullptr;
    }
}

double AAudioOutput::latency() {
    std::lock_guard<std::mutex> guard(mutex_);
    if (!stream_) return 0.0;
    // Lo escrito menos lo que ya sonó, contado por la marca de tiempo de la salida en este instante
    int64_t written = AAudioStream_getFramesWritten(stream_);
    int64_t frame = 0, when = 0;
    if (AAudioStream_getTimestamp(stream_, CLOCK_MONOTONIC, &frame, &when) == AAUDIO_OK) {
        timespec now{};
        clock_gettime(CLOCK_MONOTONIC, &now);
        int64_t nanos = int64_t(now.tv_sec) * 1000000000 + now.tv_nsec;
        double heard = double(frame) + double(nanos - when) * rate_ / 1e9;
        double late = (double(written) - heard) / rate_;
        if (late >= 0 && late < 2.0) return late;
    }
    return double(AAudioStream_getBufferSizeInFrames(stream_)) / rate_;
}

void AAudioOutput::pause() {
    std::lock_guard<std::mutex> guard(mutex_);
    if (stream_) AAudioStream_requestPause(stream_);
}

void AAudioOutput::resume() {
    std::lock_guard<std::mutex> guard(mutex_);
    if (stream_) AAudioStream_requestStart(stream_);
}

aaudio_data_callback_result_t AAudioOutput::data(AAudioStream*, void* self, void* audio, int32_t frames) {
    static_cast<AAudioOutput*>(self)->callback_(static_cast<float*>(audio), frames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AAudioOutput::error(AAudioStream*, void* self, aaudio_result_t error) {
    // Se desconectó la salida (auriculares, Bluetooth): el hilo productor la abre de nuevo, no este.
    LOG("AAudio error: %s", AAudio_convertResultToText(error));
    static_cast<AAudioOutput*>(self)->lost_ = true;
}

}  // namespace slowdown
