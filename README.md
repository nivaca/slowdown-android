Slowdown for Android
====================

Slowdown plays audio slower or faster without changing its pitch. It is made
for music: for practising a piece and learning it by ear, hearing a passage
again and again at a speed one can follow. It is the Android version of
[py-slowdown](https://github.com/nivaca/py-slowdown), and it keeps the same
engine and the same `.slx` files.

*Leer en español: [README_es.md](README_es.md).*

Installing
----------

It needs Android 12 or later, on a 64-bit ARM phone. The signed APK of each
version is in the releases, on
[GitHub](https://github.com/nivaca/slowdown-android/releases) and
[Codeberg](https://codeberg.org/nivaca/slowdown-android/releases).

[![Get it on Obtainium](docs/img/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.github.nivaca.slowdown%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fnivaca%2Fslowdown-android%22%2C%22author%22%3A%22nivaca%22%2C%22name%22%3A%22Slowdown%22%7D)

With [Obtainium](https://github.com/ImranR98/Obtainium) the phone installs the
newest release and then each new one over the old. The badge adds the app to
Obtainium in one touch, if Obtainium is installed (the link passes through
Obtainium's own redirect page, which has analytics); without it, add the app
in Obtainium by hand, with the address
`https://github.com/nivaca/slowdown-android`.

It is also in my own F-Droid repository, with the same signed APKs, so each
one updates the other: in F-Droid (or Droid-ify, Neo Store), add the
repository `https://nivaca.codeberg.page/fdroid/repo`, whose key has the
fingerprint `E930B855E234BD2DE4F083D454DA0195549005D91A89309F9A3E5510D71E2E46`; the page <https://nivaca.codeberg.page/fdroid/> adds it in
one touch.

What it does
------------

- Opens audio files and the sound of video files: MP3, M4A/AAC, MP4, FLAC,
  WAV, AIFF, Ogg, Opus and whatever else Android decodes. MP3, FLAC, WAV and
  AIFF are decoded by the app itself, a five-minute song in half a second;
  the rest by the codecs of Android, which are slower (about ten times real
  time), so their waveform fills in for a few seconds while they already
  play. Files with more than two channels are mixed down to stereo.
- The window is laid out as that of py-slowdown, and drawn with its colours
  (light or dark, as the phone is set): the waveform, darker where it has
  played, with the position in red and the bookmarks in orange; under it the
  player's buttons and the time; the knobs of the speed, the volume and the
  pitch; and the panel of the bookmarks and loops, with its two tabs. A tap
  on the waveform moves the position there; dragging scrolls; pinching zooms
  around the fingers. A knob turns by dragging up or to the right (down or
  to the left, the other way), and goes home with a double tap.
- Play, pause, stop, go to the start or to the end, back and forward by a
  skip length chosen in the menu (1 to 60 seconds; hold the button to keep
  going).
- Speed from 0.25× to 3×, changed while it plays, with the pitch kept. The
  stretching is done by the [Rubber Band Library](https://breakfastquay.com/rubberband/),
  with its R3 ("finer") engine. At 1× the file is played as it is, sample
  for sample. The button Speeds has the speeds of py-slowdown (50%, 75%,
  100%…). A lighter quality (half the processor) or the older R2 engine
  (crisp attacks) can be chosen in the menu.
- Moves the pitch up or down in semitones (−12 to +12) and cents (−50 to
  +50), also while it plays; with "Keep the formants", a voice or an
  instrument keeps its timbre.
- Repeats a selection without end and without a gap, or the whole file if
  there is none: hold a finger on the waveform and drag, or set its start and
  end with A and B at the playing position. Selections can be kept as loops
  with a name (Add, in the tab Loops).
- The volume, up to 100% (the recording's), or more with Boost: the sound
  times 1.8, or the factor chosen in the menu (Volume boost: ×1.2 to ×3), as
  in py-slowdown; the knob turns red where the sound passes the recording's.
- Bookmarks at the playing position, or anywhere by holding a finger on the
  waveform without moving it; each can have a name (hold a finger on its
  row), and one can be the first beat of the recording. They can be exported
  as a text file, as in py-slowdown.
- Remembers, for each file, its bookmarks, its loops, the point where it was
  left and how it was played, in a `.slx` file like those of py-slowdown, and
  reopens the last file when it starts. The `.slx` can be exported and
  loaded (menu), and kept in a folder chosen, such as that of the music, so
  that each one is beside its recording and travels with it to the
  computer and back.
- A keyboard or a foot pedal that sends keys works with the keys of
  py-slowdown (Space, ←/→, ↑/↓, B, I, O, L…), and the play button of
  headphones plays and pauses.
- Interface in English and in Spanish, as the phone is set.

- Plays on with the screen off, or with another app in front, with a media
  notification and the lock screen's controls.

- Counts in before playing, as a metronome (the metronome button, or C;
  hold it for its settings): the number of clicks and the tempo are chosen,
  and the tempo can follow the speed and be measured by tapping with the
  beat (T). With the recording's first beat marked (Shift+C), the recording
  comes in on the nearest beat wherever playing starts.

- Hears one part of the mix (menu > Hear): one channel, the two in one, the
  mix without its centre (where the voice and the solo usually are), only
  the bass, or everything but the bass.

Not yet in this version (it is in py-slowdown): exporting the audio.

Building
--------

It needs a JDK (17 or newer) and the Android SDK with platform 36, the NDK
29.0.14206865 and CMake 3.31.6. Write the path of the SDK in
`local.properties` (`sdk.dir=/home/me/Android/Sdk`), or set `ANDROID_HOME`.
With the phone connected and USB debugging on:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

`tools/test.sh` runs all the tests, in parallel: those of the engine,
compiled for the computer, and those of the Kotlin code, on the JVM.

Releases and signing
--------------------

`./gradlew assembleRelease` makes the release APK, shrunk with R8, and copies
it to `dist/slowdown-VERSION.apk`, replacing the older ones there. It is
signed only if the file `keystore.properties` exists in the root of the
project, with the keys `storeFile`, `storePassword`, `keyAlias` and
`keyPassword`; the key and that file are not in git. Without them, the
release APK comes out unsigned, as on F-Droid's build server.

The copy in `dist/` is also signed with OpenPGP, as
`dist/slowdown-VERSION.apk.asc`, when gpg has the secret key of the project
(the same as py-slowdown's; its public half is
[GPG-KEY-slowdown.asc](GPG-KEY-slowdown.asc)). The variable
`SLOWDOWN_GPG_KEY` names another key; without the key, the build says so and
leaves only the Android signature. To check a release:

    gpg --import GPG-KEY-slowdown.asc
    gpg --verify slowdown-VERSION.apk.asc slowdown-VERSION.apk
    gpg --verify SHA256SUMS.asc SHA256SUMS && sha256sum -c SHA256SUMS
    apksigner verify --print-certs slowdown-VERSION.apk

The Android certificate's SHA-256 is
`59f28011299559e2bc46ed38931327db41bad0b8c75a948689193c704e4e25c1`.

A new version is published with one command, after writing what is new
under `## Unreleased` in `CHANGELOG.md` and `## Sin publicar` in
`CHANGELOG_es.md`:

    python3 tools/release.py [--version X.Y] [--dry-run] [--yes]

It runs all the tests first, and stops if one fails. It raises
`versionCode` and `versionName` (1.2 to 1.3, unless `--version` says
otherwise), gives those two sections the version and today's date, puts the
version in `docs/`, and writes the notes for the stores. It commits
(`vX.Y`), signs the tag, and builds the signed APK from the tag, as F-Droid
needs; if the build fails, it undoes the commit and the tag. It writes
`dist/SHA256SUMS` and its signature `SHA256SUMS.asc`, and checks every
signature, and the tag's, as a user would, with only the public key; if one
fails, nothing is published. Then it pushes `main` and the tag to `origin`
(GitHub and Codeberg), makes the release on each forge with the APK,
`SHA256SUMS`, their `.asc` and the public key, with notes taken from the
changelog, and puts the APK in my F-Droid repository. It asks before
changing anything, and `--dry-run` only says what it would do. It needs
`gh`, `tea`, `gpg` with the key of the project, a clean `main` and
`keystore.properties`. If it stops after tagging, running it again publishes
what is missing.

F-Droid
-------

The description, the icon and the notes of each version for F-Droid are in
`fastlane/metadata/android/` (in English and Spanish). The build recipe is
`fdroid/io.github.nivaca.slowdown.yml`: it builds the tag `vVERSION` from
the folder `app`, with the NDK named there, and finds new versions by their
tags.

The build is meant to be reproducible: the engine is compiled without the
paths of the machine that builds it and without a build id, so that F-Droid,
building the same tag, gets the same APK apart from the signature, and can
publish the signed APK of the release. The release APK must be built from
the tagged commit, because the APK records the commit it was built from.

License
-------

Slowdown is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option)
any later version (`GPL-3.0-or-later`; see [LICENSE](LICENSE)).

The APK also carries software by others, all of it under licences that
allow its use in a program under the GPL 3:

| Software | Licence |
|---|---|
| [Rubber Band Library](https://breakfastquay.com/rubberband/) 4.0.0, © 2007–2024 Particular Programs Ltd. | `GPL-2.0-or-later`, used here under the GPL 3 |
| [minimp3](https://github.com/lieff/minimp3), by lieff | `CC0-1.0` |
| [dr_flac](https://github.com/mackron/dr_libs) 0.13.4 and dr_wav 0.14.6, © 2023 David Reid | `Unlicense OR MIT-0` |
| [Kotlin standard library](https://kotlinlang.org) 2.2.10, © JetBrains and contributors | `Apache-2.0` |
| [LLVM libc++](https://libcxx.llvm.org) and libc++abi, of the Android NDK r29 | `Apache-2.0 WITH LLVM-exception` |
| [Material Icons](https://github.com/google/material-design-icons) (the shapes of the buttons), © Google | `Apache-2.0` |

The full texts of these licences are in [`LICENSES/`](LICENSES), and in the
app itself (menu > Licences). [docs/THIRD-PARTY.md](docs/THIRD-PARTY.md)
says what each one is for, and lists the tools that build the app. The
sources of Rubber Band, minimp3, dr_flac and dr_wav are in
[`third_party/`](third_party), as their authors publish them; of Rubber Band
only its own files are compiled, not the code by others that it carries.

Nicolas Vaughan, nicolasvaughan@proton.me
