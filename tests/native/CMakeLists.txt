# SPDX-License-Identifier: GPL-3.0-or-later
# Las pruebas del motor (audio.cpp, engine.cpp), compiladas para el ordenador y no para el teléfono:
#   cmake -S tests/native -B build/native-tests && cmake --build build/native-tests -j && ctest --test-dir build/native-tests -j
cmake_minimum_required(VERSION 3.22)
project(slowdown_native_tests CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

set(ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../..)
set(RUBBERBAND ${ROOT}/third_party/rubberband-4.0.0)

add_library(rubberband STATIC ${RUBBERBAND}/single/RubberBandSingle.cpp)
target_include_directories(rubberband PUBLIC ${RUBBERBAND})
target_compile_definitions(rubberband PRIVATE NDEBUG)

add_library(core STATIC ${ROOT}/app/src/main/cpp/audio.cpp ${ROOT}/app/src/main/cpp/engine.cpp
            ${ROOT}/app/src/main/cpp/hearing.cpp ${ROOT}/app/src/main/cpp/equalizer.cpp ${ROOT}/app/src/main/cpp/exporter.cpp
            ${ROOT}/app/src/main/cpp/decoder.cpp)
target_include_directories(core PRIVATE ${ROOT}/third_party/minimp3 ${ROOT}/third_party/dr_libs)
set_source_files_properties(${ROOT}/app/src/main/cpp/decoder.cpp PROPERTIES COMPILE_OPTIONS
                            "-Wno-unused-function;-Wno-unused-parameter;-Wno-sign-compare")
target_include_directories(core PUBLIC ${ROOT}/app/src/main/cpp)
target_compile_options(core PRIVATE -Wall -Wextra)
target_link_libraries(core PUBLIC rubberband pthread)

enable_testing()
add_compile_definitions(DATA="${CMAKE_CURRENT_SOURCE_DIR}/data")
foreach(name audio renderer player decoder hearing equalizer export)
  add_executable(test_${name} test_${name}.cpp)
  target_link_libraries(test_${name} core)
  add_test(NAME ${name} COMMAND test_${name})
endforeach()
# (la más lenta primero, cuando se corren en paralelo)
set_tests_properties(renderer PROPERTIES COST 100)
set_tests_properties(player PROPERTIES COST 50)
