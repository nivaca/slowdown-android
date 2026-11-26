How Slowdown for Android was made
=================================

Slowdown plays audio slower or faster without changing its pitch, on an
Android phone. It is for practising music and learning it by ear. Nicolas
Vaughan made it in November 2026, as the Android version of py-slowdown,
his program for the desktop (Python and Qt): the engine of py-slowdown was
translated into C++, and the window was written again for a phone;
the current one is 1.3. Here I explain how it was made, in six stages.

Slowdown is free software, under the GNU General Public License, version 3
or later. It carries the Rubber Band Library 4.0.0, by Particular Programs
Ltd. (Breakfast Quay), under the GPL, version 2 or later (used here under
the GPL 3), which does the stretching; minimp3, by lieff (CC0); dr_flac and
dr_wav, by David Reid (Unlicense or MIT No Attribution); the Kotlin
standard library (Apache License 2.0); LLVM's libc++, linked into the
engine (Apache License 2.0 with the LLVM exceptions); and the shapes of
Google's Material Icons (Apache License 2.0). `docs/THIRD-PARTY.md` lists
them, with the tools that build the app. How the files of Transcribe! keep their
markers was learnt, for py-slowdown, from Rory O'Kane's
Transcribe-xsc-file-converter (Blue Oak Model License 1.0.0), without
taking any of its code.

Nicolas Vaughan is Associate Professor of Literature at the School of Arts
and Humanities of the Universidad de los Andes, Bogotá, Colombia.


1. A first version
------------------

The first question was whether to keep Python. There are ways to run
Python on Android, but the two things py-slowdown needs most do not go
with it: PortAudio, through which it sends the sound to the sound card,
does not exist on Android, and a thread of Python that must feed the
sound card every few milliseconds is a risk on a phone. The window, made
for a mouse and a keyboard, had to be made again in any case. So the
engine went to C++ and the window to Kotlin.

The engine of py-slowdown (`engine.py`) was translated almost line by
line into `engine.cpp`, because its design was already right and had been
tested: a renderer that makes the output audio in blocks, a copy of the
file sample for sample at 1× and Rubber Band (R3) at any other speed or
pitch, a gapless loop whose end fades into its start, short ramps against
clicks, and a player with a producer thread and a queue of 0.15 seconds,
whose blocks carry the position of the file they come from. Rubber Band is
compiled from the same source file as in py-slowdown (its single-file
build, with its own FFT), now with the compiler of the Android NDK. The
sound goes out through AAudio. When nothing plays for half a second, the
stream is paused, so as not to spend the battery; if the output changes
(headphones, Bluetooth), the stream is opened again.

The decoded audio lives in memory, in chunks of 65,536 frames that never
move once written, so that the decoder can add to the end while the
player and the waveform read what is already there, without a lock. Each
chunk keeps also the minimum and maximum of every 256 frames, for the
waveform.

The window has the waveform at the top (a tap moves the position, a drag
scrolls, a pinch zooms; holding a finger and dragging selects, holding it
still adds a bookmark), the transport buttons, the speed and the pitch
(semitones and cents), the selection with its buttons A and B, repeat and
"save as a loop", and the lists of bookmarks and loops. The keys of
py-slowdown work with a keyboard or a pedal. The texts are in English and
in Spanish, with the words of the Spanish interface of py-slowdown.

What is done with each file is kept in a `.slx`, the same XML as in
py-slowdown and with the same names (song.slx, song.mp3.slx...), so that a
file can go from the phone to the computer and back. On the phone they
live in a folder of the app; instead of a path, `<file path>` has the
address that Android gives the file.

The first build worked, and the tests of the engine, compiled for the
computer, passed after one correction in the tests themselves: they
measured the pitch on a window that reached the end of the stretched
audio.


2. On the phone
---------------

On the phone (a Moto g55, Android 16), I measured first whether R3 keeps up.
It does: at its highest quality it runs between 4.6 and 5.8 times faster
than real time, on one core; the light quality and R2, between 13 and 19
times. The tests of the engine pass on the phone as well.

Three problems appeared. Android 15 and later draw every app under the
status bar, and the top of the waveform was hidden under the title bar;
the title bar went into the layout, and the window takes the size of the
bars as padding.

The second was the decoding. With the codecs of Android, a five-minute MP3
took 23 seconds to read, and a FLAC of 40 seconds took 46 seconds and gave
only half of its sound. Measuring each call showed the cause: every buffer
goes to the codec in another process and back, which here costs about a
millisecond, and the extractor of Android cuts a FLAC into tens of
thousands of small pieces. The FLAC was also read wrongly, because its
codec said it gave samples in floating point and gave them in 16 bits.
MP3, FLAC, WAV and AIFF are now decoded in the app itself, with minimp3,
dr_flac and dr_wav (each one a single header file, chosen at a commit of
some months before): the five-minute MP3 takes half a second, the FLAC a
tenth of a second. The format is recognised by its first bytes, not by the
name, which Android does not always give. The rest (AAC, Opus, Vorbis, the
sound of a video) still goes to the codecs of Android, at about ten times
real time, so its waveform fills in while it already plays. minimp3 also
removes the delay and the padding of the encoder, as FFmpeg does in
py-slowdown, so that a bookmark falls at the same moment on both.

The third: with no service for playing in the background, the app went on
playing after one left it, until Android froze it at some moment. Until
there is such a service, it pauses when it leaves the screen, and the
screen stays on while it plays.


3. The window of the desktop
----------------------------

The first window was made in the way of Android, and it did not look like
py-slowdown; so it was drawn again on the model of the latest version of
py-slowdown (1.3). The colours of the waveform are those of its
`waveform.py`, light or dark as the phone is set: the waveform in blue,
darker where it has played, drawn at the scale of the loudest peak; the
position in red; the bookmarks as dashed orange lines with their names in
small flags under the ruler, and the first beat in green; the selection in
green, with a band also in the ruler. The ruler has minor ticks, as there.

Under the waveform come, as on the desktop, the buttons of the player (now
with Stop, and with Repeat, which with no selection repeats the whole file,
as in py-slowdown 1.3, and keeps it in the `.slx` as `<repeat
whole="true"/>`), the time, and the knobs. The knob is a copy of that of
py-slowdown: a short pointer at the edge of its face, the value in the
middle, ticks around it, and an uneven scale around the value straight up
(the speed: 25 to 100% on the left, 100 to 300% on the right). On the
desktop the pointer follows the mouse; under a finger a small knob is
hidden, so here it turns by dragging up or to the right, and a double tap
takes it home. There are knobs for the speed (with 1× and the menu
Speeds), the semitones and the cents (with ø), and the volume, with Boost,
as in py-slowdown: the square of the knob's value, times the factor of
Boost, and the knob in red where the sound passes the recording's. The
factor, as in the desktop's Settings, is chosen in the menu (Volume boost:
×1.2, 1.5, 1.8, the first one, 2, 2.5 or 3). The bookmarks
and the loops went into a panel like the desktop's dock: two tabs, a table
with Time and Label, and the buttons Add, Remove and Export… (a text file
like py-slowdown's).


4. The release cycle
--------------------

The releases follow those of my other two Android apps, py-words and
Wiktionary Reader. `tools/release.py`, taken from Wiktionary Reader's, makes a
release in one command: it runs the tests, raises the version, writes the
notes for the stores from the changelogs (`tools/store_notes.py`), commits
and signs the tag, builds the signed APK from the tag, writes `SHA256SUMS`,
checks every signature with only the public key, as a user would, and
publishes on GitHub and Codeberg; then it puts the APK in my own F-Droid
repository. The APK is signed with a key of its own for Android (RSA, 4096
bits), kept out of git, and with the OpenPGP key of py-slowdown. The
F-Droid recipe names the NDK, and the engine is compiled without the paths
of the machine and without a build id, so that F-Droid's build of a tag can
match the published APK.

I took care with the licences. I checked which files of Rubber Band are
really compiled: only its own 62, all with the same notice (GPL 2 or
later); none of the code by others in its `src/ext/`. The Kotlin standard
library goes into the APK, and so does LLVM's libc++, linked statically
into the engine; the shapes of the buttons come from Material Icons. Their
full texts are in `LICENSES/`, taken as their authors publish them (those of
LLVM from the notice of the NDK, the Unlicense and MIT-0 from dr_flac), and
they go into the APK too, where a Licences screen shows them, because the
GPL and the Apache License ask that the licence travel with the program.
`docs/THIRD-PARTY.md`, the READMEs, the About box and the notes of each
release say the same, and a test checks that the list in the app, the texts
and the documents agree.


5. What was missing
-------------------

After the first two releases I took up what the phone still lacked, one
thing at a time.

The `.slx` could not leave the phone, although the READMEs said it
travelled with the recording. Now it can be exported and loaded, and kept
in a folder chosen with Android's folder picker, such as that of the
music, beside each recording, as on the desktop; the app's own copy stays.
The places where the `.slx` lives became an interface of their own
(`SlxFolder`), with the app's folder and the chosen one behind it.

Playing with the screen off needed a service in the foreground, with a
media session and its notification. What plays moved out of the window,
into `Playback`, so that the window can close while it plays and attach to
it when it comes back; audio focus and unplugged headphones went with it.

The count-in of py-slowdown came next, with its rules copied from
`countin.py` (the tempo that follows the speed, tapping the tempo, the
recording coming in on the nearest beat of its first beat); then the ways
of hearing, its `hearing.py` in C++, with the same linear-phase filters,
applied with Rubber Band's FFT; then the export of the audio, rendered
apart in a thread, in WAV, or in M4A with Android's AAC encoder. FLAC, MP3
and Opus, which py-slowdown also writes, are not there: Android cannot
write a FLAC file, has no MP3 encoder, and its Opus encoder takes only
48 kHz.

`tools/check_reproducible.py` builds a release again from its tag, in
another folder and without the key, and compares it with the APK on GitHub,
entry by entry, as F-Droid would. Version 1.1 is reproducible.

One thing failed. An AAC file is read at about ten times real time,
because each buffer goes to Android's codec in another process and back. I
split the file in four parts, each with its own codec, and the decoding
went eight times faster; but the samples, compared with those of one
codec, differed, and two runs of the parallel decoding differed from each
other. Part of it was mine: duplicated file descriptors share their
position, and the extractors moved each other's; with a file opened again
for each part, the first part came out exact. The rest was not: with
several codecs at once Android's service gave samples that changed from
one run to the next, and at times it refused a second codec. So the AAC is
read by one codec, as before.

6. What the desktop gained
--------------------------

While the phone got the things of stage 5, py-slowdown went on, from 1.3
to 1.6, with an equalizer, knobs with menus, the loops on the waveform, a
start screen, the files of Transcribe! and other smaller things. I went
through the list and asked of each one whether a phone can have it.

Most of it could come. The equalizer is `equalizer.py` in C++, as the ways
of hearing had been: the five gains are points of a curve, the curve
becomes a linear-phase filter of 80 ms, and the filter is applied with
Rubber Band's FFT, block after block, with a fade when the gains change.
NumPy's `irfft` and `hanning` had to be written out by hand, and a test
checks that each band lifts its own frequency by what it says and that the
blocks give the same as one convolution. The player takes the gains from
the window under its lock and applies them after the ways of hearing, as
the desktop does; the export makes up the delay of both filters. On the
desktop the equalizer lives in a panel Sound at the side of the window; on
the phone there is no side, so Sound became a third tab of the panel
below, with the channels and the filter of the menu Hear, and the five
knobs under a curve drawn from the same formula as the engine's.

The rest was mostly the window. The loops are drawn at the foot of the
waveform, in the colours of Okabe and Ito; zoomed in, a strip above shows
the whole file. Here I found that a view moved by hand jumped back to the
position at the next frame, even in pause: the view followed the position
on every frame, not only when the position moved. The knobs got a menu (on
the phone, a finger held still, since there is no right button) and the
speed knob its detents; the clock switches with a tap; a value changed from
the keyboard is shown large on the waveform. The start screen keeps a
small summary of each recording's waveform in the preferences, because the
app has no cache of peaks as the desktop has.

The files of Transcribe! needed one change of approach. On the desktop a
`.xsc` names its recording by a full path, and py-slowdown opens it, or the
file of the same name beside the `.xsc`. On a phone neither path means
anything, since files come through Android's picker; so the app takes the
recording already open, if it has that name, and otherwise asks for it.
The reader itself is `transcribe.py` in Kotlin, and its test reads the
same `.xsc` as py-slowdown's test.

Some things did not come. Compact mode is a small window always on top of
the others, which a phone does not have. The arrow of Save and the question
before closing a file make no sense where everything is saved as it is
done. The knobs reached with Tab, and the program's own icons for the
buttons, which on the desktop replaced Qt's icons, had no use here: the
app already draws Material's shapes in the theme's colours.

One more problem came from Android's classes. The names on the start
screen came out tiny: `TextPaint` has a field `density` of its own, and in
Kotlin a block `apply { textSize = 14 * density }` took that one, which is
1, instead of the screen's.

Testing
-------

There are 60 tests. Seven are programs in C++ that test the
engine compiled for the computer, run by CTest: the audio in memory (its
chunks, reading across them, the peaks of the waveform), the renderer (the
copy bit for bit at 1×; the length and the pitch of the stretched sound at
three speeds and in the three qualities; a semitone up; the loop without a
gap; a seek with its ramp; the waiting while it decodes; the count-in),
the player with an output of test (pause, seek, the end, a slower speed,
the loop, the count-in, the sleeping of the output) and the decoders (one
second of A 440 in MP3, FLAC and WAV, its length and its pitch, and a 5.1
WAV mixed down to stereo), the ways of hearing (the kernels of the filters,
the channels, the bass with and without, and that block after block gives
the same as the whole filter at once), and the export (a valid WAV, the
same samples at 1×, twice as long at 0.5×, a selection, and the filter's
delay made up, also with the equalizer), and the equalizer (its curve,
each band's gain, flat untouched, the blocks a convolution, a change
without a click). Thirty-five tests in Kotlin, run on the JVM, check the
`.slx` (that one written by py-slowdown is read, that what is written is
read back, the numbers written as Python writes them, the names of the
files and how the right one is found, the whole file repeated), the
bookmarks exported as text, the format of the times and of the boost
factors, that the licences in the app, in `LICENSES/` and in the documents
agree, how their texts are fitted to the screen, the `.slx` in any folder,
the rules of the count-in and its bars and beats, the equalizer's curve,
its presets and its place in the `.slx`, the files of Transcribe!, and the
recent recordings. Eighteen tests in Python check the tools of the release
(`tools/zenodo.py`). `tools/test.sh` runs the three kinds at once, each
test in a process of its own.

The tests of the engine were also compiled for the phone and run there,
and two small programs measured on it the speed of Rubber Band and of the
decoders. The app itself was tried on the phone: opening a file with the
picker of Android, playing it, changing the speed and the pitch while it
plays (the sound system counted no underrun), selecting a passage and
repeating it, and its `.slx`, written when leaving the app. Later, on the
same phone: the `.slx` written beside the recording in the Music folder and
read from there first; playing on with the screen off and in another app,
and the media keys; the count-in, its beat on the waveform and its dialog;
the ways of hearing at 0.6× with the pitch raised (no underrun); and an
export to WAV and to M4A, checked with ffprobe and decoded by FFmpeg.
The things of stage 6 were tried on Android's emulator (an x86_64 image of
Android 16, which runs the ARM engine by translation), since the phone had
a build signed with another key: the tab Sound and a preset while playing,
the `.slx` with its `<equalizer>`, a loop on the waveform, zooming to the
selection with the strip of the whole file, the value shown large, the
clock, a knob's menu, the start screen, and a `.xsc` imported through the
picker.

Without the libraries in `third_party/`, the texts of `LICENSES/` and the
documentation, Slowdown has 106 files of its own (code, layouts, texts,
build files, the store texts, tests and tools) with about 12,100 lines:
2,790 of C++ in the engine, 4,010 of Kotlin in the window, 1,770 in the
tests and 1,610 in the tools.
