# Changelog

All notable changes to Slowdown, the Android version of
[py-slowdown](https://github.com/nivaca/py-slowdown). The source is on
[GitHub](https://github.com/nivaca/slowdown-android) and
[Codeberg](https://codeberg.org/nivaca/slowdown-android).

## Unreleased

- The `.slx` can now leave the phone, and come in: menu > Export the .slx
  (with the name py-slowdown gives it, song.slx), and Load a .slx, which
  takes the bookmarks, loops and settings of a `.slx` made on the computer
  (it asks first if it is of another recording). Menu > Folder of the .slx
  files can keep them in a folder chosen, such as that of the music, so that
  each one is beside its recording, as on the desktop, and travels with it;
  the app's own copy stays as well.

## 1.1 — 2026-11-25

- In Spanish, the row of the speed and the volume no longer runs off the
  screen ("Velocidad" was cut and "Refuerzo" broken in two): the knobs and
  the buttons beside them are a little smaller.
- The language of the app can be chosen apart from the phone's, in
  Android's settings of the app (English or Spanish).
- Screenshots for F-Droid, in English and Spanish.

## 1.0 — 2026-11-25

The first version.

- Plays audio slower or faster (0.25× to 3×) without changing its pitch,
  with the R3 engine of the Rubber Band Library, as py-slowdown; at 1× the
  file plays as it is, sample for sample.
- Moves the pitch in semitones and cents, keeping the formants if asked;
  a volume knob with Boost (×1.2 to ×3, chosen in the menu).
- The window of py-slowdown 1.3, for a phone: the waveform with its
  colours, the player's buttons, the knobs, and the panel of bookmarks and
  loops.
- Repeats a selection, or the whole file, without a gap; bookmarks with
  names, and loops kept with a name.
- Reads MP3, FLAC, WAV and AIFF by itself (a five-minute song in half a
  second), and the rest with Android's codecs.
- Keeps how each file was played in a `.slx` file, the same as
  py-slowdown's.
- Interface in English and in Spanish.
