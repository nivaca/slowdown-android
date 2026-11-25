Open-source software used by Slowdown
=====================================

*(Español: [TERCEROS_es.md](TERCEROS_es.md))*

Slowdown is free software, under the GNU General Public License, version 3
or later (GPL-3.0-or-later). It is built on the open-source software listed
here: what the APK carries, and the tools that build and test it. Licences
are given as SPDX identifiers, as each project declares them. The full texts
are in [`LICENSES/`](../LICENSES), and inside the app (menu > Licences).

All of these licences allow their use in a program under the GPL 3. The
Rubber Band Library is under the GPL 2 "or (at your option) any later
version", and so is used here under the GPL 3. Software under the Apache
License 2.0 may be combined with a program under the GPL 3 (not with one
under the GPL 2 only). minimp3 is dedicated to the public domain (CC0), and
dr_flac and dr_wav may be taken under either of their two licences
(Unlicense or MIT No Attribution).


What the APK carries
--------------------

| Software | Version | Licence | Copyright | What Slowdown does with it |
|---|---|---|---|---|
| Slowdown | | GPL-3.0-or-later | © 2026 Nicolas Vaughan | The app itself |
| [Rubber Band Library](https://breakfastquay.com/rubberband/) | 4.0.0 | GPL-2.0-or-later (or a commercial licence from Particular Programs) | © 2007–2024 Particular Programs Ltd. | Changes the speed without changing the pitch (its R3 and R2 engines) |
| [minimp3](https://github.com/lieff/minimp3) | commit `ea99364` (2026-07-27) | CC0-1.0 | lieff, dedicated to the public domain | Reads MP3 files |
| [dr_flac](https://github.com/mackron/dr_libs) | 0.13.4 (commit `dfe8377`, 2026-08-31) | Unlicense OR MIT-0 | © 2023 David Reid | Reads FLAC files |
| [dr_wav](https://github.com/mackron/dr_libs) | 0.14.6 (commit `dfe8377`, 2026-08-31) | Unlicense OR MIT-0 | © 2023 David Reid | Reads WAV, RF64 and AIFF files |
| [Kotlin standard library](https://kotlinlang.org) | 2.2.10 | Apache-2.0 | © JetBrains s.r.o. and Kotlin contributors | The language of the window |
| [LLVM libc++ and libc++abi](https://libcxx.llvm.org), of the Android NDK r29 | 29.0.14206865 | Apache-2.0 WITH LLVM-exception (older parts: NCSA, as the NDK's notice says) | The LLVM Project contributors | The C++ library, linked into the engine |
| [Material Icons](https://github.com/google/material-design-icons) | | Apache-2.0 | © Google LLC | The shapes of the buttons (play, pause, the arrows…), drawn from its paths |

The sources of Rubber Band, minimp3, dr_flac and dr_wav are in
[`third_party/`](../third_party), each with its licence, exactly as their
authors publish them. Only Rubber Band's own files are compiled: its
single-file build (`single/RubberBandSingle.cpp`, with its own FFT and
resampler); none of the code that it carries from others (`src/ext/`:
KissFFT, the Speex resampler, getopt…) goes into the app. The 62 files that
are compiled all carry the same notice, of Particular Programs Ltd. under
the GPL 2 or later.

The launcher icon is that of py-slowdown, by Nicolas Vaughan
(GPL-3.0-or-later).

The app uses no other library: the window is made with Android's own
widgets, the sound goes out through Android's AAudio, and the formats that
the app does not read itself (AAC, Opus, Vorbis, the sound of a video) are
decoded by Android's codecs. These belong to the phone's system, not to the
APK.


Tools that build and test it
----------------------------

| Software | Licence | What for |
|---|---|---|
| [Gradle](https://gradle.org) and its wrapper (`gradle/wrapper/`, in the source) | Apache-2.0 | Builds the app |
| [Android Gradle Plugin](https://developer.android.com/build) and the Android SDK (build-tools, platform 36) | Apache-2.0 (the SDK under the Android Software Development Kit License) | Builds, signs and checks the APK |
| [Android NDK](https://developer.android.com/ndk) r29 (Clang, LLVM) | Apache-2.0 WITH LLVM-exception, and others in its NOTICE | Compiles the engine |
| [CMake](https://cmake.org) | BSD-3-Clause | Drives the compilation of the engine |
| [Kotlin compiler](https://kotlinlang.org) | Apache-2.0 | Compiles the window |
| [JUnit](https://junit.org/junit4/) 4.13.2 | EPL-1.0 | The tests of the Kotlin code |
| [GnuPG](https://gnupg.org) | GPL-3.0-or-later | Signs the APK, `SHA256SUMS` and the tag |
| [gh](https://cli.github.com) and [tea](https://gitea.com/gitea/tea) | MIT | Make the releases on GitHub and Codeberg |
| [fdroidserver](https://gitlab.com/fdroid/fdroidserver) | AGPL-3.0-or-later | Makes the own F-Droid repository |
| [FFmpeg](https://ffmpeg.org) | GPL-3.0-or-later (as built by RPM Fusion; it is not in the app) | Made the short test files of `tests/native/data/` |
| [Inkscape](https://inkscape.org) | GPL-2.0-or-later | Draws the icon of the stores from its SVG |
