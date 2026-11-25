// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Cómo se tocó cada archivo, en un archivo .slx, el mismo de py-slowdown.
 *
 * El .slx es un XML con la posición, la velocidad, la altura, la manera de oír,
 * la selección, la cuenta previa, los marcadores y los bucles de un archivo de
 * audio. En el teléfono se guardan en una carpeta de la aplicación, con los
 * nombres que les da py-slowdown (canción.slx, canción.mp3.slx o
 * canción.mp3.LETRAS.slx): uno escrito aquí se puede abrir en el ordenador, y al
 * revés. En lugar de la ruta del archivo, <file path> lleva su dirección de
 * Android (content://…).
 */
package io.github.nivaca.slowdown

import java.io.File
import java.io.StringWriter
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class Bookmark(var time: Double, var label: String = "", var firstBeat: Boolean = false)

data class Loop(var start: Double, var end: Double, var label: String = "")

data class CountIn(var on: Boolean = false, var bpm: Int = 100, var offset: Double = 0.0)

/** How a file was played: what the .slx keeps. */
data class Session(
    var position: Double = 0.0,
    var speed: Double = 1.0,
    var pitch: Double = 0.0,  // semitonos, con los cents como decimales
    var formants: Boolean = false,
    var channels: String = "both",
    var filter: String = "none",
    var volume: Int? = null,
    var selection: Pair<Double, Double>? = null,
    var repeat: Boolean = false,
    var countIn: CountIn? = null,
    val bookmarks: MutableList<Bookmark> = mutableListOf(),
    val loops: MutableList<Loop> = mutableListOf(),
)

/** The audio file a .slx is of: its address (or path), its name and its size in bytes (-1 if unknown). */
data class Source(val path: String, val name: String, val size: Long = -1)

object Sidecar {
    const val FORMAT = "1"
    const val EXTENSION = ".slx"

    /** The names its file can have, in order: canción.slx, canción.mp3.slx, canción.mp3.LETTERS.slx. */
    fun candidates(source: Source, folder: File): List<File> {
        val stem = source.name.substringBeforeLast('.', source.name)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.path.toByteArray())
        val letters = digest.joinToString("") { "%02x".format(it) }.take(8)
        return listOf(
            File(folder, "$stem$EXTENSION"),
            File(folder, "${source.name}$EXTENSION"),
            File(folder, "${source.name}.$letters$EXTENSION"),
        )
    }

    private fun owner(file: File): Triple<String, String, Long>? {
        val root = parse(file) ?: return null
        val element = child(root, "file") ?: return null
        return Triple(element.getAttribute("path"), element.getAttribute("name"),
            element.getAttribute("size").toLongOrNull() ?: -1)
    }

    /** The .slx of SOURCE in FOLDER, if there is one: the one with its address, or else one with its name (and
     * its size, if both are known). */
    fun find(source: Source, folder: File): File? {
        var byName: File? = null
        for (file in candidates(source, folder)) {
            val (path, name, size) = (if (file.isFile) owner(file) else null) ?: continue
            if (path == source.path) return file
            if (name == source.name && byName == null && (size < 0 || source.size < 0 || size == source.size)) {
                byName = file  // (el mismo archivo, que llegó por otro camino, o el de py-slowdown)
            }
        }
        return byName
    }

    /** Where to write the .slx of SOURCE: its own, or the first name that is free. */
    fun target(source: Source, folder: File): File {
        find(source, folder)?.let { return it }
        val all = candidates(source, folder)
        return all.firstOrNull { !it.exists() } ?: all.last()
    }

    fun load(source: Source, folder: File): Session? = find(source, folder)?.let { read(it) }

    private fun parse(file: File): Element? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        val root = factory.newDocumentBuilder().parse(file).documentElement
        if (root.tagName == "py-slowdown") root else null
    } catch (_: Exception) {
        null
    }

    private fun child(parent: Element, name: String): Element? {
        val nodes = parent.getElementsByTagName(name)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.parentNode == parent) return node as Element
        }
        return null
    }

    private fun children(parent: Element?, name: String): List<Element> {
        if (parent == null) return emptyList()
        val nodes = parent.childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == name }
    }

    private fun number(element: Element?, name: String, default: Double): Double {
        if (element == null || !element.hasAttribute(name)) return default
        return element.getAttribute(name).toDoubleOrNull()?.takeIf { it.isFinite() } ?: default
    }

    private fun flag(element: Element?, name: String): Boolean =
        element != null && element.getAttribute(name).lowercase() in setOf("true", "1", "yes")

    /** What the file keeps, or null if it cannot be read (or is not one of py-slowdown). */
    fun read(file: File): Session? {
        val root = parse(file) ?: return null
        val session = Session(position = maxOf(0.0, number(child(root, "position"), "seconds", 0.0)))
        val playback = child(root, "playback")
        session.speed = number(playback, "speed", 1.0)
        session.pitch = number(playback, "pitch", 0.0)
        session.formants = flag(playback, "formants")
        child(root, "volume")?.let { if (it.hasAttribute("percent")) session.volume = number(it, "percent", 100.0).toInt() }
        child(root, "hearing")?.let {
            session.channels = it.getAttribute("channels").ifEmpty { "both" }
            session.filter = it.getAttribute("filter").ifEmpty { "none" }
        }
        child(root, "selection")?.let {
            val start = number(it, "start", -1.0)
            val end = number(it, "end", -1.0)
            if (start in 0.0..<end) {
                session.selection = start to end
                session.repeat = flag(it, "repeat")
            }
        }
        if (session.selection == null && flag(child(root, "repeat"), "whole")) {
            session.repeat = true  // (sin selección: el archivo entero)
        }
        child(root, "countin")?.let {
            session.countIn = CountIn(flag(it, "on"), number(it, "bpm", 100.0).toInt(), number(it, "offset", 0.0))
        }
        for (mark in children(child(root, "bookmarks"), "bookmark")) {
            val time = number(mark, "time", -1.0)
            if (time >= 0) session.bookmarks.add(Bookmark(time, mark.textContent.trim(), flag(mark, "first-beat")))
        }
        for (loop in children(child(root, "loops"), "loop")) {
            val start = number(loop, "start", -1.0)
            val end = number(loop, "end", -1.0)
            if (start in 0.0..<end) session.loops.add(Loop(start, end, loop.textContent.trim()))
        }
        return session
    }

    // Como repr(round(x, 3)) en Python: 1.0, 0.75, 83.25
    private fun text(value: Double): String {
        val rounded = Math.round(value * 1000) / 1000.0
        return rounded.toBigDecimal().stripTrailingZeros().toPlainString().let { if ('.' in it) it else "$it.0" }
    }

    private fun escape(s: String, attribute: Boolean = true): String {
        val out = StringBuilder()
        for (c in s) {
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append(if (attribute) "&quot;" else "\"")
                '\n' -> out.append(if (attribute) "&#10;" else "\n")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** The .slx of SESSION, as text. */
    fun format(session: Session, source: Source, version: String, now: LocalDateTime = LocalDateTime.now()): String {
        val stamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        val w = StringWriter()
        w.append("<?xml version='1.0' encoding='UTF-8'?>\n")
        w.append("<py-slowdown version=\"${escape(version)}\" format=\"$FORMAT\">\n")
        w.append("  <file path=\"${escape(source.path)}\" name=\"${escape(source.name)}\"")
        if (source.size >= 0) w.append(" size=\"${source.size}\"")
        w.append(" />\n")
        w.append("  <saved>${now.truncatedTo(ChronoUnit.SECONDS).format(stamp)}</saved>\n")
        w.append("  <position seconds=\"${text(session.position)}\" />\n")
        w.append("  <playback speed=\"${text(session.speed)}\" pitch=\"${text(session.pitch)}\" " +
            "formants=\"${session.formants}\" />\n")
        w.append("  <hearing channels=\"${escape(session.channels)}\" filter=\"${escape(session.filter)}\" />\n")
        session.volume?.let { w.append("  <volume percent=\"$it\" />\n") }
        session.selection?.let { (start, end) ->
            w.append("  <selection start=\"${text(start)}\" end=\"${text(end)}\" repeat=\"${session.repeat}\" />\n")
        }
        if (session.selection == null && session.repeat) w.append("  <repeat whole=\"true\" />\n")
        session.countIn?.let {
            w.append("  <countin on=\"${it.on}\" bpm=\"${it.bpm}\" offset=\"${text(it.offset)}\" />\n")
        }
        val marks = session.bookmarks.sortedBy { it.time }
        if (marks.isEmpty()) w.append("  <bookmarks />\n") else {
            w.append("  <bookmarks>\n")
            for (mark in marks) {
                w.append("    <bookmark time=\"${text(mark.time)}\"")
                if (mark.firstBeat) w.append(" first-beat=\"true\"")
                if (mark.label.isEmpty()) w.append(" />\n") else w.append(">${escape(mark.label, false)}</bookmark>\n")
            }
            w.append("  </bookmarks>\n")
        }
        val loops = session.loops.sortedWith(compareBy({ it.start }, { it.end }))
        if (loops.isEmpty()) w.append("  <loops />\n") else {
            w.append("  <loops>\n")
            for (loop in loops) {
                w.append("    <loop start=\"${text(loop.start)}\" end=\"${text(loop.end)}\"")
                if (loop.label.isEmpty()) w.append(" />\n") else w.append(">${escape(loop.label, false)}</loop>\n")
            }
            w.append("  </loops>\n")
        }
        w.append("</py-slowdown>\n")
        return w.toString()
    }

    /** Write the .slx of SOURCE in FOLDER (entero o nada); its file. */
    fun save(session: Session, source: Source, folder: File, version: String): File {
        val file = target(source, folder)
        folder.mkdirs()
        val temporary = File(folder, file.name + ".tmp")
        temporary.writeText(format(session, source, version), Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw java.io.IOException("cannot write ${file.path}")
        }
        return file
    }
}
