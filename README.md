Slowdown for Android
====================

Slowdown plays audio slower or faster without changing its pitch. It is made
for music: for practising a piece and learning it by ear, hearing a passage
again and again at a speed one can follow. It is the Android version of
[py-slowdown](https://github.com/nivaca/py-slowdown), and it keeps the same
engine and the same `.slx` files.

*Leer en español: [README_es.md](README_es.md).*

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
  reopens the last file when it starts.
- A keyboard or a foot pedal that sends keys works with the keys of
  py-slowdown (Space, ←/→, ↑/↓, B, I, O, L…), and the play button of
  headphones plays and pauses.
- Interface in English and in Spanish, as the phone is set.

Not yet in this version (they are in py-slowdown): the count-in, the ways of
hearing (one channel, without the centre, the bass), exporting the audio, and
playing with the screen off: for now the screen stays on while it plays, and
it pauses when one leaves the app.

Building and running
--------------------

It needs the Android SDK with the NDK (29) and CMake (3.31), and Java 17 or
later. With the phone connected and USB debugging on:

    ./gradlew installDebug
    adb shell am start -n io.github.nivaca.slowdown/.MainActivity

`tools/test.sh` runs all the tests, in parallel: those of the engine,
compiled for the computer, and those of the Kotlin code, on the JVM.

It needs Android 12 or later, on a 64-bit ARM phone.

License
-------

Slowdown is free software, under the GNU General Public License, version 3
or later (see [LICENSE](LICENSE)). It includes the Rubber Band Library 4.0.0
(GPL, version 2 or later), and the decoders minimp3 (CC0) and dr_flac and
dr_wav (public domain, or MIT No Attribution), in `third_party/`.

Nicolas Vaughan, nicolasvaughan@proton.me
