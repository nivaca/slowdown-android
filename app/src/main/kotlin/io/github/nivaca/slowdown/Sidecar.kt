// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Cómo se tocó cada archivo, en un archivo .slx, el mismo de py-slowdown.
 *
 * El .slx es un XML con la posición, la velocidad, la altura, la manera de oír,
 * el ecualizador, la selección, la cuenta previa, los marcadores y los bucles de un archivo de
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
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
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
    var eq: List<Int> = Equalizer.FLAT,  // las cinco bandas, en dB
    var eqOn: Boolean = true,  // (apagado, las ganancias se guardan pero no suenan)
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
    fun names(source: Source): List<String> {
        val stem = source.name.substringBeforeLast('.', source.name)
        val digest = MessageDigest.getInstance("SHA-256").digest(source.path.toByteArray())
        val letters = digest.joinToString("") { "%02x".format(it) }.take(8)
        return listOf("$stem$EXTENSION", "${source.name}$EXTENSION", "${source.name}.$letters$EXTENSION")
    }

    fun candidates(source: Source, folder: File): List<File> = names(source).map { File(folder, it) }

    /** (address, name, size) of the audio file that the .slx TEXT is of, or null. */
    fun owner(text: String): Triple<String, String, Long>? {
        val root = parse(text) ?: return null
        val element = child(root, "file") ?: return null
        return Triple(element.getAttribute("path"), element.getAttribute("name"),
            element.getAttribute("size").toLongOrNull() ?: -1)
    }

    /** The name of the .slx of SOURCE in FOLDER, if there is one: the one with its address, or else one with its
     * name (and its size, if both are known). */
    fun find(source: Source, folder: SlxFolder): String? {
        var byName: String? = null
        for (name in names(source)) {
            val (path, audio, size) = folder.read(name)?.let { owner(it) } ?: continue
            if (path == source.path) return name
            if (audio == source.name && byName == null && (size < 0 || source.size < 0 || size == source.size)) {
                byName = name  // (el mismo archivo, que llegó por otro camino, o el de py-slowdown)
            }
        }
        return byName
    }

    fun find(source: Source, folder: File): File? = find(source, FileFolder(folder))?.let { File(folder, it) }

    /** The name to write the .slx of SOURCE with: its own, or the first name that is free. */
    fun target(source: Source, folder: SlxFolder): String {
        find(source, folder)?.let { return it }
        val all = names(source)
        return all.firstOrNull { !folder.exists(it) } ?: all.last()
    }

    fun load(source: Source, folder: SlxFolder): Session? = find(source, folder)?.let { name -> folder.read(name)?.let { read(it) } }

    fun load(source: Source, folder: File): Session? = load(source, FileFolder(folder))

    private fun parse(text: String): Element? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isExpandEntityReferences = false
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(text))).documentElement
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
    fun read(file: File): Session? = try { read(file.readText()) } catch (_: java.io.IOException) { null }

    /** What the .slx TEXT keeps, or null if it is not one of py-slowdown. */
    fun read(text: String): Session? {
        val root = parse(text) ?: return null
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
        child(root, "equalizer")?.let {
            val gains = it.getAttribute("gains").trim().split(Regex("\\s+")).mapNotNull(String::toIntOrNull)
            if (gains.size == Equalizer.BANDS.size && gains.any { g -> g != 0 }) {
                session.eq = Equalizer.clamp(gains)
                session.eqOn = it.getAttribute("on").lowercase() !in setOf("false", "0", "no")
            }
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
        if (!Equalizer.isFlat(session.eq)) {
            // (las cinco bandas, de 60 Hz a 12 kHz, en dB)
            w.append("  <equalizer gains=\"${session.eq.joinToString(" ")}\" on=\"${session.eqOn}\" />\n")
        }
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

    /** Write the .slx of SOURCE in FOLDER; the name it got. */
    fun save(session: Session, source: Source, folder: SlxFolder, version: String): String {
        val name = target(source, folder)
        folder.write(name, format(session, source, version))
        return name
    }

    fun save(session: Session, source: Source, folder: File, version: String): File =
        File(folder, save(session, source, FileFolder(folder), version))
}

/** Where the .slx files are kept: the app's own folder, or one that the user chose (FolderTree.kt). */
interface SlxFolder {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun exists(name: String): Boolean
}

/** A folder of the file system. */
class FileFolder(private val dir: File) : SlxFolder {
    override fun read(name: String): String? =
        File(dir, name).takeIf { it.isFile }?.let { try { it.readText() } catch (_: java.io.IOException) { null } }

    override fun write(name: String, text: String) {
        // (entero o nada, aunque la aplicación muera mientras escribe)
        dir.mkdirs()
        val temporary = File(dir, "$name.tmp")
        temporary.writeText(text, Charsets.UTF_8)
        if (!temporary.renameTo(File(dir, name))) {
            temporary.delete()
            throw java.io.IOException("cannot write ${File(dir, name).path}")
        }
    }

    override fun exists(name: String) = File(dir, name).exists()
}
