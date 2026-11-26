// SPDX-License-Identifier: GPL-3.0-or-later
// Los archivos de Transcribe! (.xsc): lo mismo que lee de ellos py-slowdown (su tests/test_transcribe.py).
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranscribeTest {
    private val xsc = """Transcribe!,6089,Seventh String Software
Transcribe!,GTK,9,60,7,S,0

SectionStart,Main
SaveDate,2026-07-28 20:06
SoundFileName,canción.mp3,Nix,/nowhere/canción.mp3
Loops,0:0,0:0
SectionEnd,Main

SectionStart,View0
FX_Tuning,1,0,0,0,-32,0,60,50,0,440
FX_Transposition,1,1,0,0,0
FX_Speed,1,0,75000,0
SelectionLeft,48000,0:00:01.000000
SelectionRight,96000,0:00:02.500000
SectionEnd,View0

SectionStart,Markers
Howmany,7
S,7350,1,,0,0:00:00.500000
B,45829,1,,0,0:00:01.000000
M,86471,1,,0,0:00:01.500000
S,118105,0,Head\C again,0,0:00:02.000000
B,147432,1,,0,0:00:02.500000
M,176400,0,Coda,0,0:00:03.000000
S,204503,1,,0,0:00:04.000000
SectionEnd,Markers

SectionStart,Loops
Howmany,3
MostRecent,-1
L,1,0,0,0,The bridge,White,,0:00:01.000000,0:00:03.000000
L,2,0,0,0,,White,,0:00:00.000000,0:00:00.000000
L,3,0,0,0,,Red,,0:00:04.000000,0:00:05.250000
SectionEnd,Loops
"""

    @Test
    fun readsWhatTheDesktopReads() {
        val file = Transcribe.read(xsc, "First beat")!!
        assertEquals("canción.mp3", file.recording)  // (de la ruta del ordenador, solo el nombre)
        val s = file.session
        assertEquals(0.75, s.speed, 1e-9)
        assertEquals(-0.32, s.pitch, 1e-9)
        assertEquals(1.0 to 2.5, s.selection)
        assertEquals(1.0, s.position, 1e-9)
        // Las secciones (A, B… las que no tienen nombre), el compás con nombre, y el primer pulso
        assertEquals(listOf(Triple(0.5, "A", false), Triple(1.0, "First beat", true), Triple(2.0, "Head, again", false),
            Triple(3.0, "Coda", false), Triple(4.0, "C", false)), s.bookmarks.map { Triple(it.time, it.label, it.firstBeat) })
        // Pulsos y compases cada 0,5 s: 120 BPM, con la cuenta apagada
        assertEquals(CountIn(false, 120, 0.0), s.countIn)
        assertEquals(listOf(Loop(1.0, 3.0, "The bridge"), Loop(4.0, 5.25, "")), s.loops)
    }

    @Test
    fun aLittleFile() {
        // Sin marcadores ni bucles, a 1×, sin afinar, sin selección: solo la posición
        val text = xsc.substringBefore("SectionStart,Markers").replace("75000", "100000").replace(",-32,", ",0,")
            .replace("0:00:02.500000", "0:00:01.000000")
        val s = Transcribe.read(text)!!.session
        assertEquals(1.0, s.speed, 1e-9)
        assertEquals(0.0, s.pitch, 1e-9)
        assertNull(s.selection)
        assertNull(s.countIn)
        assertEquals(1.0, s.position, 1e-9)
        assertEquals(0, s.bookmarks.size + s.loops.size)
    }

    @Test
    fun notOneOfTranscribe() {
        assertNull(Transcribe.read("Something else\n"))
    }

    @Test
    fun theirFieldsAndTimes() {
        assertEquals(listOf("a,b", "c\\d", "", "e"), Transcribe.fields("a\\Cb,c\\\\d,,e"))
        assertEquals(3723.25, Transcribe.seconds("1:02:03.250000")!!, 1e-9)
        assertNull(Transcribe.seconds("soon"))
        assertEquals(listOf("A", "Z", "AA", "AB"), listOf(0, 25, 26, 27).map(Transcribe::letters))
    }
}
