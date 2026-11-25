// SPDX-License-Identifier: GPL-3.0-or-later
// El puente entre Kotlin (Engine.kt) y el motor: un solo reproductor para toda la aplicación.
#include <jni.h>

#include <mutex>
#include <string>

#include "decoder.h"
#include "engine.h"
#include "output.h"

using namespace slowdown;

namespace {

std::mutex mutex;  // abrir y cerrar, de a uno
Player* player() {
    static Player instance(std::make_unique<AAudioOutput>());
    return &instance;
}
std::unique_ptr<Decoder> decoder;

std::shared_ptr<Audio> audio() { return player()->audio(); }

}  // namespace

#define FN(type, name) extern "C" JNIEXPORT type JNICALL Java_io_github_nivaca_slowdown_Engine_##name

FN(jstring, nativeOpen)(JNIEnv* env, jobject, jint fd, jlong offset, jlong length) {
    std::lock_guard<std::mutex> guard(mutex);
    player()->unload();
    decoder.reset();
    std::string error;
    decoder = Decoder::open(fd, offset, length, error);
    if (!decoder) return env->NewStringUTF(error.c_str());
    if (!player()->load(decoder->audio())) return env->NewStringUTF("output");
    return nullptr;
}

FN(void, nativeClose)(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> guard(mutex);
    player()->unload();
    decoder.reset();
}

FN(jint, rate)(JNIEnv*, jobject) { auto a = audio(); return a ? a->rate : 0; }
FN(jint, channels)(JNIEnv*, jobject) { auto a = audio(); return a ? a->channels : 0; }
FN(jlong, decoded)(JNIEnv*, jobject) { auto a = audio(); return a ? a->decoded() : 0; }
FN(jboolean, done)(JNIEnv*, jobject) { auto a = audio(); return a && a->done(); }
FN(jboolean, failed)(JNIEnv*, jobject) { auto a = audio(); return a && a->failed(); }
FN(jfloat, loudest)(JNIEnv*, jobject) { auto a = audio(); return a ? a->loudest() : 0.0f; }
FN(jdouble, duration)(JNIEnv*, jobject) { return player()->duration(); }

FN(void, peaks)(JNIEnv* env, jobject, jdouble start, jdouble fpp, jfloatArray out) {
    auto a = audio();
    jsize size = env->GetArrayLength(out);
    jfloat* values = env->GetFloatArrayElements(out, nullptr);
    if (a) a->peaks(start, fpp, size / 2, values);
    else std::fill(values, values + size, 0.0f);
    env->ReleaseFloatArrayElements(out, values, 0);
}

FN(void, play)(JNIEnv*, jobject, jint beats, jdouble bpm, jdouble offset) { player()->play(beats, bpm, offset); }
FN(void, pause)(JNIEnv*, jobject) { player()->pause(); }
FN(void, stop)(JNIEnv*, jobject) { player()->stop(); }
FN(void, seek)(JNIEnv*, jobject, jdouble seconds) { player()->seek(seconds); }
FN(jboolean, playing)(JNIEnv*, jobject) { return player()->playing(); }
FN(jboolean, takeEnded)(JNIEnv*, jobject) { return player()->take_ended(); }
FN(jdouble, position)(JNIEnv*, jobject) { return player()->position(); }

FN(void, setSpeed)(JNIEnv*, jobject, jdouble speed) { player()->set_speed(speed); }
FN(void, setPitch)(JNIEnv*, jobject, jdouble semitones) { player()->set_pitch(semitones); }
FN(void, setFormants)(JNIEnv*, jobject, jboolean on) { player()->set_formants(on); }
FN(void, setQuality)(JNIEnv*, jobject, jint quality) { player()->set_quality(quality); }
FN(void, setHearing)(JNIEnv*, jobject, jint channels, jint filter) { player()->set_hearing(channels, filter); }
FN(void, setVolume)(JNIEnv*, jobject, jdouble volume) { player()->set_volume(volume); }

FN(void, setLoop)(JNIEnv*, jobject, jdouble start, jdouble end) {
    if (start < 0) player()->set_loop(std::nullopt);
    else player()->set_loop(std::make_pair(start, end));
}

FN(jboolean, looping)(JNIEnv*, jobject) { return player()->loop().has_value(); }

// El pulso que suena en una cuenta previa, por 100, más cuántos son; -1 si no hay cuenta.
FN(jint, counting)(JNIEnv*, jobject) {
    auto c = player()->counting();
    return c ? c->first * 100 + c->second : -1;
}

FN(jdouble, maxEarlier)(JNIEnv*, jobject, jint rate, jdouble bpm) { return max_earlier(rate, bpm); }
