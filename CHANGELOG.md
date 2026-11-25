# Changelog

All notable changes to Slowdown, the Android version of
[py-slowdown](https://github.com/nivaca/py-slowdown). The source is on
[GitHub](https://github.com/nivaca/slowdown-android) and
[Codeberg](https://codeberg.org/nivaca/slowdown-android).

## Unreleased

- `tools/zenodo.py`: the description of the deposit is now the whole
  README of the tag, in HTML. The draft also gets the contributor (the
  project leader), the languages, the copyright, the repository on GitHub,
  the programming language and the development status, these last through
  Zenodo's records API (its deposit API doesn't take them). The token can
  be kept in the desktop keyring (`secret-tool store --label="Zenodo
  token" service zenodo`) instead of `$ZENODO_TOKEN`; `tools/zenodo.py
  check-token` says whether there is one.
- `tools/zenodo.py`: the description of the deposit leaves out the line
  that points to the README in other languages and the Screenshots section,
  whose images Zenodo does not show.

## 1.2 — 2026-11-25

- The `.slx` can now leave the phone, and come in: menu > Export the .slx
  (with the name py-slowdown gives it, song.slx), and Load a .slx, which
  takes the bookmarks, loops and settings of a `.slx` made on the computer
  (it asks first if it is of another recording). Menu > Folder of the .slx
  files can keep them in a folder chosen, such as that of the music, so that
  each one is beside its recording, as on the desktop, and travels with it;
  the app's own copy stays as well.
- Plays on with the screen off, or with another app in front: a playback
  service with a media notification (back, play/pause, forward) on the lock
  screen and in the notifications, and the buttons of headphones. The app
  no longer pauses when one leaves it; going back to it shows what is
  playing. It still pauses when the headphones are unplugged, or when
  another app takes the sound (a call).
- The count-in of py-slowdown: clicks before playing (the metronome button;
  hold it for its settings: how many clicks, the tempo, which can follow the
  speed, and the offset of the recording). The tempo can be measured by
  tapping with the beat (Tap, or T); with the first beat of the recording
  marked (Shift+C), the recording comes in on the nearest beat wherever
  playing starts, and Go to the start goes to that beat. The beat that
  sounds is shown large on the waveform. Each file keeps its tempo in its
  `.slx`.
- The ways of hearing of py-slowdown (menu > Hear), to pick out one part of
  a mix: one channel, both in one (mono), without the centre (where the
  voice and the solo usually are), only the bass, or everything but the
  bass. The filters are the same linear-phase ones, at 250 Hz; each file
  keeps its way in its `.slx`.
- Exports the recording, or the selection, as it sounds (menu > Export the
  audio): at the speed and pitch chosen, and, if asked, as it is heard
  (the channels, the filter, the volume). WAV (16 bits) or M4A (AAC, with
  Android's encoder). py-slowdown's FLAC, MP3 and Opus are not here yet:
  Android cannot write a FLAC file, has no MP3 encoder, and its Opus
  encoder takes only 48 kHz.
- The Licences screen in Spanish says all it says in Spanish (two notes
  were in English); new screenshots, with the metronome.

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
