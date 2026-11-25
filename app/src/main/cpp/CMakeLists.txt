# SPDX-License-Identifier: GPL-3.0-or-later
# El motor (libslowdown.so): Rubber Band, en un solo archivo, con su FFT y su remuestreador propios, y el
# reproductor, sobre AAudio y los códecs del NDK.
cmake_minimum_required(VERSION 3.22)
project(slowdown CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(RUBBERBAND ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/rubberband-4.0.0)

add_library(rubberband STATIC ${RUBBERBAND}/single/RubberBandSingle.cpp)
target_include_directories(rubberband PUBLIC ${RUBBERBAND})
target_compile_definitions(rubberband PRIVATE NDEBUG)
# Siempre optimizado, también en la versión de depuración: si no, R3 no alcanza el tiempo real
target_compile_options(rubberband PRIVATE -O3)

add_library(slowdown SHARED audio.cpp engine.cpp output.cpp decoder.cpp jni.cpp)
target_compile_options(slowdown PRIVATE -Wall -Wextra -O2)
target_link_libraries(slowdown rubberband aaudio mediandk log)
target_link_options(slowdown PRIVATE -Wl,-z,max-page-size=16384)
