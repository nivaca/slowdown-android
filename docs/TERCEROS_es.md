Software de código abierto que usa Slowdown
===========================================

*(English: [THIRD-PARTY.md](THIRD-PARTY.md))*

Slowdown es software libre, bajo la Licencia Pública General de GNU,
versión 3 o posterior (GPL-3.0-or-later). Se apoya en el software de código
abierto que se lista aquí: lo que lleva el APK, y las herramientas con que
se compila y se prueba. Las licencias se dan con sus identificadores SPDX,
tal como las declara cada proyecto. Los textos completos están en
[`LICENSES/`](../LICENSES), y dentro de la aplicación (menú > Licencias).

Todas estas licencias permiten usarlo en un programa bajo la GPL 3. La
biblioteca Rubber Band está bajo la GPL 2 «o (a su elección) cualquier
versión posterior», y por eso se usa aquí bajo la GPL 3. El software bajo
la Licencia Apache 2.0 puede combinarse con un programa bajo la GPL 3 (no
con uno solo bajo la GPL 2). minimp3 está dedicado al dominio público
(CC0), y dr_flac y dr_wav pueden tomarse bajo cualquiera de sus dos
licencias (Unlicense o MIT sin atribución).


Lo que lleva el APK
-------------------

| Software | Versión | Licencia | Derechos | Para qué lo usa Slowdown |
|---|---|---|---|---|
| Slowdown | | GPL-3.0-or-later | © 2026 Nicolas Vaughan | La aplicación misma |
| [Rubber Band Library](https://breakfastquay.com/rubberband/) | 4.0.0 | GPL-2.0-or-later (o una licencia comercial de Particular Programs) | © 2007–2024 Particular Programs Ltd. | Cambia la velocidad sin cambiar la altura (sus motores R3 y R2) |
| [minimp3](https://github.com/lieff/minimp3) | commit `ea99364` (2026-07-27) | CC0-1.0 | lieff, dedicado al dominio público | Lee los archivos MP3 |
| [dr_flac](https://github.com/mackron/dr_libs) | 0.13.4 (commit `dfe8377`, 2026-08-31) | Unlicense OR MIT-0 | © 2023 David Reid | Lee los archivos FLAC |
| [dr_wav](https://github.com/mackron/dr_libs) | 0.14.6 (commit `dfe8377`, 2026-08-31) | Unlicense OR MIT-0 | © 2023 David Reid | Lee los archivos WAV, RF64 y AIFF |
| [Kotlin standard library](https://kotlinlang.org) | 2.2.10 | Apache-2.0 | © JetBrains s.r.o. y los colaboradores de Kotlin | El lenguaje de la ventana |
| [LLVM libc++ y libc++abi](https://libcxx.llvm.org), del NDK de Android r29 | 29.0.14206865 | Apache-2.0 WITH LLVM-exception (las partes antiguas: NCSA, como dice el aviso del NDK) | Los colaboradores del proyecto LLVM | La biblioteca de C++, enlazada en el motor |
| [Material Icons](https://github.com/google/material-design-icons) | | Apache-2.0 | © Google LLC | Las formas de los botones (tocar, pausa, las flechas…), dibujadas con sus trazos |

El código de Rubber Band, minimp3, dr_flac y dr_wav está en
[`third_party/`](../third_party), cada uno con su licencia, tal como lo
publican sus autores. De Rubber Band solo se compilan sus propios archivos:
su compilación de un solo archivo (`single/RubberBandSingle.cpp`, con su FFT
y su remuestreador propios); nada del código que trae de otros (`src/ext/`:
KissFFT, el remuestreador de Speex, getopt…) entra en la aplicación. Los 62
archivos que se compilan llevan todos el mismo aviso, de Particular
Programs Ltd. bajo la GPL 2 o posterior.

El icono de la aplicación es el de py-slowdown, de Nicolas Vaughan
(GPL-3.0-or-later).

La aplicación no usa ninguna otra biblioteca: la ventana se hace con los
controles de Android, el sonido sale por AAudio de Android, y los formatos
que la aplicación no lee por sí misma (AAC, Opus, Vorbis, el sonido de un
video) los decodifican los códecs de Android. Esos son del sistema del
teléfono, no del APK.


Herramientas con que se compila y se prueba
-------------------------------------------

| Software | Licencia | Para qué |
|---|---|---|
| [Gradle](https://gradle.org) y su envoltorio (`gradle/wrapper/`, en el código) | Apache-2.0 | Compila la aplicación |
| [Android Gradle Plugin](https://developer.android.com/build) y el SDK de Android (build-tools, plataforma 36) | Apache-2.0 (el SDK, bajo la Android Software Development Kit License) | Compila, firma y comprueba el APK |
| [NDK de Android](https://developer.android.com/ndk) r29 (Clang, LLVM) | Apache-2.0 WITH LLVM-exception, y otras en su NOTICE | Compila el motor |
| [CMake](https://cmake.org) | BSD-3-Clause | Dirige la compilación del motor |
| [Compilador de Kotlin](https://kotlinlang.org) | Apache-2.0 | Compila la ventana |
| [JUnit](https://junit.org/junit4/) 4.13.2 | EPL-1.0 | Las pruebas del código en Kotlin |
| [GnuPG](https://gnupg.org) | GPL-3.0-or-later | Firma el APK, `SHA256SUMS` y la etiqueta |
| [gh](https://cli.github.com) y [tea](https://gitea.com/gitea/tea) | MIT | Hacen las versiones en GitHub y en Codeberg |
| [fdroidserver](https://gitlab.com/fdroid/fdroidserver) | AGPL-3.0-or-later | Hace el repositorio propio de F-Droid |
| [FFmpeg](https://ffmpeg.org) | GPL-3.0-or-later (como lo compila RPM Fusion; no está en la aplicación) | Hizo los archivos cortos de prueba de `tests/native/data/` |
| [Inkscape](https://inkscape.org) | GPL-2.0-or-later | Dibuja el icono de las tiendas desde su SVG |
