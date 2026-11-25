// SPDX-License-Identifier: GPL-3.0-or-later
// El .slx: lo que escribe py-slowdown se lee aquí, y lo que se escribe aquí se lee igual.
package io.github.nivaca.slowdown

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarTest {
    private val folder: File = Files.createTempDirectory("slx").toFile().apply { deleteOnExit() }

    // Uno como los de py-slowdown 1.3 (el ejemplo de su sidecar.py)
    private val desktop = """<?xml version="1.0" encoding="UTF-8"?>
<py-slowdown version="1.2.0" format="1">
  <file path="/home/x/canción.mp3" name="canción.mp3" size="4861932" modified="2026-11-20T18:04:11"/>
  <saved>2026-11-24T19:30:02</saved>
  <position seconds="83.25"/>
  <playback speed="0.75" pitch="-2.0" formants="true"/>
  <hearing channels="both" filter="bass"/>
  <volume percent="120"/>
  <selection start="95.0" end="103.5" repeat="true"/>
  <countin on="true" bpm="96" offset="0.0"/>
  <bookmarks>
    <bookmark time="0.82" first-beat="true">Primer pulso</bookmark>
    <bookmark time="21.5">La segunda estrofa &amp; algo</bookmark>
  </bookmarks>
  <loops><loop start="95.0" end="103.5">El puente</loop></loops>
</py-slowdown>
"""

    @Test
    fun readsTheDesktopFormat() {
        val file = File(folder, "canción.slx").apply { writeText(desktop) }
        val s = Sidecar.read(file)!!
        assertEquals(83.25, s.position, 0.0)
        assertEquals(0.75, s.speed, 0.0)
        assertEquals(-2.0, s.pitch, 0.0)
        assertTrue(s.formants)
        assertEquals("bass", s.filter)
        assertEquals(120, s.volume)
        assertEquals(95.0 to 103.5, s.selection)
        assertTrue(s.repeat)
        assertEquals(CountIn(true, 96, 0.0), s.countIn)
        assertEquals(listOf(Bookmark(0.82, "Primer pulso", true), Bookmark(21.5, "La segunda estrofa & algo")), s.bookmarks)
        assertEquals(listOf(Loop(95.0, 103.5, "El puente")), s.loops)
    }

    @Test
    fun writesWhatItReads() {
        val source = Source("content://media/1", "Tema <1>.mp3", 1234)
        val s = Session(position = 12.3456, speed = 0.6, pitch = 2.3, formants = false, selection = 1.0 to 2.5)
        s.bookmarks += Bookmark(3.0, "Uno \"dos\"\ntres")
        s.bookmarks += Bookmark(1.0)
        s.loops += Loop(1.0, 2.5, "")
        val file = Sidecar.save(s, source, folder, "0.1.0")
        assertEquals("Tema <1>.slx", file.name)
        val back = Sidecar.read(file)!!
        assertEquals(12.346, back.position, 0.0)  // (milésimas, como py-slowdown)
        assertEquals(0.6, back.speed, 0.0)
        assertEquals(2.3, back.pitch, 0.0)
        assertFalse(back.repeat)
        assertEquals(listOf(1.0, 3.0), back.bookmarks.map { it.time })
        assertEquals("Uno \"dos\"\ntres", back.bookmarks[1].label)
        assertEquals(1, back.loops.size)
        assertNull(back.countIn)
        assertEquals(file, Sidecar.find(source, folder))
        assertNotNull(Sidecar.load(source, folder))
    }

    @Test
    fun numbersLikePython() {
        val text = Sidecar.format(Session(speed = 1.0, pitch = -12.0, position = 0.1 + 0.2), Source("p", "a.mp3"), "0.1.0",
            LocalDateTime.of(2026, 11, 25, 10, 0, 0, 5))
        assertTrue(text, "speed=\"1.0\" pitch=\"-12.0\"" in text)
        assertTrue(text, "<position seconds=\"0.3\" />" in text)
        assertTrue(text, "<saved>2026-11-25T10:00:00</saved>" in text)
        assertFalse(text, "size=" in text)
    }

    @Test
    fun namesAreThoseOfTheDesktop() {
        val source = Source("content://a/song.mp3", "song.mp3", 10)
        val names = Sidecar.candidates(source, folder).map { it.name }
        assertEquals("song.slx", names[0])
        assertEquals("song.mp3.slx", names[1])
        assertTrue(names[2].matches(Regex("""song\.mp3\.[0-9a-f]{8}\.slx""")))
    }

    @Test
    fun anotherFileKeepsItsOwn() {
        // song.slx es de song.flac: el de song.mp3 va a song.mp3.slx
        val flac = Source("content://a/song.flac", "song.flac", 99)
        val mp3 = Source("content://a/song.mp3", "song.mp3", 10)
        Sidecar.save(Session(speed = 0.5), flac, folder, "0.1.0")
        assertNull(Sidecar.find(mp3, folder))
        val file = Sidecar.save(Session(speed = 0.8), mp3, folder, "0.1.0")
        assertEquals("song.mp3.slx", file.name)
        assertEquals(0.5, Sidecar.load(flac, folder)!!.speed, 0.0)
        assertEquals(0.8, Sidecar.load(mp3, folder)!!.speed, 0.0)
    }

    @Test
    fun foundByNameWhenItCameAnotherWay() {
        // El mismo archivo, abierto desde otra aplicación (otra dirección): por su nombre y su tamaño
        val first = Source("content://a/1", "piece.ogg", 500)
        Sidecar.save(Session(speed = 0.7), first, folder, "0.1.0")
        assertEquals(0.7, Sidecar.load(Source("content://b/2", "piece.ogg", 500), folder)!!.speed, 0.0)
        assertNull(Sidecar.load(Source("content://b/3", "piece.ogg", 501), folder))
    }

    @Test
    fun repeatsTheWholeFile() {
        // Sin selección, <repeat whole="true"/>, como py-slowdown 1.3
        val source = Source("content://a/w", "whole.mp3", 1)
        val file = Sidecar.save(Session(repeat = true), source, folder, "0.1.0")
        assertTrue("<repeat whole=\"true\" />" in file.readText())
        assertTrue(Sidecar.read(file)!!.repeat)
        assertFalse("<repeat" in Sidecar.format(Session(), source, "0.1.0"))
    }

    @Test
    fun exportsTheBookmarksAsTheDesktop() {
        val text = MainActivity.exportText(listOf(Bookmark(58.0, "Guitar solo"), Bookmark(21.5, "Second verse")), "song.mp3")
        assertEquals("# song.mp3\n0:21.5\tSecond verse\n0:58.0\tGuitar solo\n", text)
    }

    @Test
    fun notOneOfOurs() {
        val file = File(folder, "x.slx").apply { writeText("<other/>") }
        assertNull(Sidecar.read(file))
        val broken = File(folder, "y.slx").apply { writeText("<py-slowdown") }
        assertNull(Sidecar.read(broken))
    }
}
