// SPDX-License-Identifier: GPL-3.0-or-later
// Cuánto tarda la decodificación de cada archivo, con los códecs del teléfono (solo para Android):
//   decode_bench ARCHIVO...
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>

#include <chrono>
#include <cstdio>
#include <string>
#include <thread>

#include "decoder.h"

int main(int argc, char** argv) {
    // (los códecs responden por binder: un programa suelto, a diferencia de la aplicación, debe abrir sus hilos)
    if (void* lib = dlopen("libbinder_ndk.so", RTLD_NOW))
        if (auto start = reinterpret_cast<void (*)()>(dlsym(lib, "ABinderProcess_startThreadPool"))) start();
    for (int i = 1; i < argc; ++i) {
        int fd = open(argv[i], O_RDONLY);
        auto t0 = std::chrono::steady_clock::now();
        std::string error;
        auto decoder = slowdown::Decoder::open(fd, 0, -1, error);
        close(fd);
        if (!decoder) {
            std::printf("%s: %s\n", argv[i], error.c_str());
            continue;
        }
        auto audio = decoder->audio();
        while (!audio->done()) std::this_thread::sleep_for(std::chrono::milliseconds(5));
        double took = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
        double seconds = double(audio->decoded()) / audio->rate;
        std::printf("%s: %.1f s in %.2f s (%.0f times real time)\n", argv[i], seconds, took, seconds / took);
    }
    return 0;
}
