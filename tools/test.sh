#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Todas las pruebas, en paralelo: las del motor en C++ (compiladas para este
# ordenador, cada una en su proceso, tantas a la vez como procesadores, la más
# lenta primero) y las de Kotlin en la JVM (un proceso por procesador).
set -euo pipefail
cd "$(dirname "$0")/.."
jobs=$(nproc)

native() {
    cmake -S tests/native -B build/native-tests -DCMAKE_BUILD_TYPE=Release >/dev/null
    cmake --build build/native-tests -j "$jobs" >/dev/null
    ctest --test-dir build/native-tests -j "$jobs" --output-on-failure
}

jvm() {
    ./gradlew --quiet testDebugUnitTest
}

# Las dos a la vez; falla si falla una
mkdir -p build
native > build/native-tests.log 2>&1 & n=$!
jvm > build/jvm-tests.log 2>&1 & j=$!
status=0
wait $n || { echo "native tests FAILED:"; cat build/native-tests.log; status=1; }
wait $j || { echo "JVM tests FAILED:"; cat build/jvm-tests.log; status=1; }
if [ $status -eq 0 ]; then
    grep -E 'tests passed' build/native-tests.log
    total=$(grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' | paste -sd+ | bc)
    echo "JVM: $total tests passed"
fi
exit $status
