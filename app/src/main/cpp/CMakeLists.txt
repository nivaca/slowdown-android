# SPDX-License-Identifier: GPL-3.0-or-later
# El motor (libslowdown.so): Rubber Band, en un solo archivo, con su FFT y su remuestreador propios, y el
# reproductor, sobre AAudio y los códecs del NDK.
cmake_minimum_required(VERSION 3.22)
project(slowdown CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compilación reproducible (F-Droid compila en otra carpeta y compara su APK con el publicado): sin las rutas de
# esta máquina en el código ni en la depuración, y sin el «build id», que se calcula sobre ellas.
get_filename_component(TOP ${CMAKE_CURRENT_SOURCE_DIR}/../../../.. ABSOLUTE)
add_compile_options(-ffile-prefix-map=${TOP}=. -ffile-prefix-map=${CMAKE_BINARY_DIR}=build)
add_link_options(-Wl,--build-id=none)

set(RUBBERBAND ${TOP}/third_party/rubberband-4.0.0)

add_library(rubberband STATIC ${RUBBERBAND}/single/RubberBandSingle.cpp)
target_include_directories(rubberband PUBLIC ${RUBBERBAND})
target_compile_definitions(rubberband PRIVATE NDEBUG)
# Siempre optimizado, también en la versión de depuración: si no, R3 no alcanza el tiempo real
target_compile_options(rubberband PRIVATE -O3)

add_library(slowdown SHARED audio.cpp engine.cpp hearing.cpp output.cpp decoder.cpp codec.cpp jni.cpp)
# Los decodificadores propios, de un solo archivo cada uno: minimp3 (CC0), dr_flac y dr_wav (dominio público)
target_include_directories(slowdown PRIVATE ${TOP}/third_party/minimp3
                                            ${TOP}/third_party/dr_libs)
set_source_files_properties(decoder.cpp PROPERTIES COMPILE_OPTIONS "-O3;-Wno-unused-function;-Wno-unused-parameter;-Wno-sign-compare")
target_compile_options(slowdown PRIVATE -Wall -Wextra -O2)
target_link_libraries(slowdown rubberband aaudio mediandk log)
target_link_options(slowdown PRIVATE -Wl,-z,max-page-size=16384)
