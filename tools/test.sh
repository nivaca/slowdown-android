#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Todas las pruebas, en paralelo: las del motor en C++ (compiladas para este
# ordenador, cada una en su proceso, tantas a la vez como procesadores, la más
# lenta primero), las de Kotlin en la JVM (un proceso por procesador) y las de
# las herramientas en Python (tests/test_*.py).
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

python_tools() {
    python3 -m unittest discover -s tests -p 'test_*.py'
}

# Las tres a la vez; falla si falla una
mkdir -p build
native > build/native-tests.log 2>&1 & n=$!
jvm > build/jvm-tests.log 2>&1 & j=$!
python_tools > build/python-tests.log 2>&1 & p=$!
status=0
wait $n || { echo "native tests FAILED:"; cat build/native-tests.log; status=1; }
wait $j || { echo "JVM tests FAILED:"; cat build/jvm-tests.log; status=1; }
wait $p || { echo "Python tests FAILED:"; cat build/python-tests.log; status=1; }
if [ $status -eq 0 ]; then
    grep -E 'tests passed' build/native-tests.log
    echo "Python: $(grep -oE '^Ran [0-9]+ tests?' build/python-tests.log | grep -oE '[0-9]+') tests passed"
    total=$(grep -ho 'tests="[0-9]*"' app/build/test-results/testDebugUnitTest/*.xml | grep -o '[0-9]*' | paste -sd+ | bc)
    echo "JVM: $total tests passed"
fi
exit $status
