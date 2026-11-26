# Changelog

All notable changes to Slowdown, the Android version of
[py-slowdown](https://github.com/nivaca/py-slowdown). The source is on
[GitHub](https://github.com/nivaca/slowdown-android) and
[Codeberg](https://codeberg.org/nivaca/slowdown-android).

## 1.4 — 2026-11-26

A pause goes back to where playing started, with Continue from the pause
and Play from the last pause, as in py-slowdown; a .slx can be loaded with
no recording open; and the menu items that cannot be used are grey on every
phone.

- **A pause goes back to where playing started**, as in py-slowdown (1.3
  and 1.7): Play then plays the same passage again. While paused, a red
  dotted line with a pause sign at its foot marks where it was paused, and
  **Continue from the pause** goes on from there: hold the Play button, or
  menu > Continue from the pause, or Shift+Space. With a repeated selection
  it goes back inside it; moved while it plays, to the new place; at the
  end of the file, too. **Play from the last pause** (a checkbox in the
  menu, off by default, kept for every file) makes Play itself go on from
  the dotted line; moved by hand while paused, it plays from there. The
  notification and the headphones do the same as the Play button.
- **Load a .slx…** works with no recording open, as Import .xsc… does: it
  reads which recording the .slx is of, asks for it, and opens it with what
  the .slx keeps.
- The menu items that cannot be used yet (Load a .slx…, Save, the exports,
  Close the file, while no recording is open) are now shown in grey. Some
  phones, Motorola's among them, drew them like the others, so that Load a
  .slx… seemed to do nothing.
- `tools/release.py` now publishes each release on Zenodo
  (`tools/zenodo.py upload --publish`), instead of leaving a draft;
  `--zenodo-draft` still leaves one. `tools/zenodo.py publish` publishes a
  draft already there (by default the one waiting with the project's title).

## 1.3 — 2026-11-26

What py-slowdown gained in its versions 1.4 to 1.6, where a phone can have it.

- **A five-band equalizer**, that of py-slowdown, in a new tab Sound of the
  panel (also menu > Hear > Equalizer): 60 Hz, 250 Hz, 1 kHz, 4 kHz and
  12 kHz, each from −12 to +12 dB, on knobs, with a curve of what it does
  from 20 Hz to 20 kHz, On (to compare), Flat, and Presets: those of
  py-slowdown for music (Bass up, Hear the bass line, Voice and solos
  forward, Bright, Warm, Cut the hiss) and one's own, saved by name and
  removed again. The same linear-phase filter as on the desktop, applied by
  the engine block after block, with a fade when it changes. Each file keeps
  it in its `.slx`, in the same `<equalizer>` as py-slowdown; an export as
  it is heard applies it, without its 40 ms of delay. The tab Sound has the
  channels and the filter of menu > Hear too, the two always agreeing; it
  says "· EQ" while the equalizer changes the sound.
- **Reset the sound** (menu, or Ctrl+Shift+N): speed 100%, the recording's
  pitch, the formants off, both channels without a filter and the
  equalizer flat, at once; the volume stays.
- **The loops on the waveform**: each saved loop is a band of its own
  colour at the foot of the waveform, with its name; a tap on it selects
  it, repeats it and goes to its start.
- **The whole file above a closer view**: zoomed in, a strip above the
  waveform shows the whole file, the part in view shaded and the position
  in red; a tap or a drag there shows that part, without moving the
  position. A view moved by hand no longer jumps back to the position while
  it is paused.
- **Zoom to the selection** (menu, or Z) and **Select the whole file**
  (menu, or Ctrl+A).
- **A start screen**: without a file, the waveform lists the recordings
  opened last, each with a small picture of its waveform; a tap opens one;
  one that is no longer there is greyed.
- **A clock that switches**: a tap on the time shows the time left, and,
  with a first beat, the bar and the beat (at the count-in's tempo, as many
  beats in a bar as it has clicks); remembered.
- **A menu on every knob**: holding a finger still on a knob offers its
  usual values (the chosen speeds; the intervals of 1, 2, 3, 5, 7 and 12
  semitones; steps of cents, of volume and of decibels), the one now
  ticked, and Back to its usual one.
- **The speed knob stops at the chosen speeds**: dragged, it stays at one
  of the list (50%, 75%, 100%…) while it passes within a few degrees of it.
- **The value just changed, large on the waveform**: a speed changed from
  the keyboard, 1× or the menu of speeds, or a pitch changed from the
  keyboard, is shown in the middle of the waveform for a moment, then fades.
- **Files of Transcribe!** (`.xsc`): menu > Import .xsc takes their
  sections and named measures as bookmarks, their first beat and the tempo
  of their beats, their loops, their selection, their speed and their
  tuning, kept from then on in the `.slx`. The `.xsc` names its recording
  by a path of the computer, so the app takes the recording open, if it has
  that name, or asks for it.
- **Tap** (in the count-in's settings) shows the tempo tapped ("120 BPM"),
  until another file is opened.

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
