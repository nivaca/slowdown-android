// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Los archivos de Transcribe! (.xsc), leídos como lo que guardaría un .slx (el
 * transcribe.py de py-slowdown, en Kotlin).
 *
 * Un .xsc es texto, en líneas de campos separados por comas (una coma dentro de
 * un campo va como \C, una barra como \\), agrupadas en secciones
 * (SectionStart,Nombre ... SectionEnd,Nombre). De él se toma el nombre de la
 * grabación (SoundFileName, en Main), la velocidad (FX_Speed: 100000 es 1×), la
 * afinación (FX_Tuning, en centésimas de semitono), la selección (SelectionLeft
 * y SelectionRight), los marcadores (Markers: las secciones, y los compases que
 * tienen un nombre propio; los pulsos dan el primer pulso y el tempo, la mediana
 * de sus intervalos) y los bucles guardados (Loops).
 *
 * Seventh String Software no publica el formato. La forma de los marcadores se
 * aprendió de Transcribe-xsc-file-converter, de Rory O'Kane (BlueOak-1.0.0),
 * como en py-slowdown, sin copiar su código.
 *
 * En el teléfono la ruta que guarda el .xsc no sirve (es la del ordenador): de
 * ella se toma solo el nombre, y la grabación es la abierta, si se llama así, o
 * la que se elija.
 */
package io.github.nivaca.slowdown

object Transcribe {
    const val EXTENSION = ".xsc"

    /** What a .xsc gives: the name of its recording, and what the .slx would keep. */
    data class File(val recording: String, val session: Session)

    private val TIME = Regex("""^(\d+):(\d{1,2}):(\d{1,2}(?:\.\d*)?)$""")

    /** The seconds of a time of Transcribe! (H:MM:SS.ffffff), or null if TEXT is not one. */
    fun seconds(text: String): Double? {
        val found = TIME.matchEntire(text.trim()) ?: return null
        val (hours, minutes, rest) = found.destructured
        return hours.toInt() * 3600 + minutes.toInt() * 60 + rest.toDouble()
    }

    // Los campos de una línea, con \C (coma) y \\ (barra) ya resueltos
    fun fields(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' -> {
                    val next = line.getOrNull(i + 1)
                    when (next) {
                        'C' -> current.append(',')
                        '\\' -> current.append('\\')
                        null -> current.append('\\')
                        else -> current.append('\\').append(next)
                    }
                    i += 1
                }
                c == ',' -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i += 1
        }
        fields += current.toString()
        return fields
    }

    private fun sections(text: String): Map<String, List<List<String>>> {
        val sections = mutableMapOf<String, MutableList<List<String>>>()
        var current: String? = null
        for (line in text.lines()) {
            if (line.isBlank()) continue
            val f = fields(line.trimEnd('\r', '\n'))
            when {
                f[0] == "SectionStart" && f.size > 1 -> {
                    current = f[1]
                    sections.getOrPut(f[1]) { mutableListOf() }
                }
                f[0] == "SectionEnd" -> current = null
                current != null -> sections.getValue(current) += f
            }
        }
        return sections
    }

    private fun value(lines: List<List<String>>, key: String): List<String>? =
        lines.firstOrNull { it[0] == key }?.drop(1)

    private fun number(values: List<String>?, index: Int): Double? = values?.getOrNull(index)?.toDoubleOrNull()

    // A, B, … Z, AA, AB…: como Transcribe! nombra solo las secciones
    fun letters(n: Int): String {
        var name = ""
        var k = n + 1
        while (k > 0) {
            val rest = (k - 1) % 26
            k = (k - 1) / 26
            name = ('A' + rest) + name
        }
        return name
    }

    private fun round3(x: Double) = Math.round(x * 1000) / 1000.0

    /** Whether BYTES look like a file of Transcribe!. */
    fun looksLike(text: String) = text.lines().take(3).any { it.startsWith("Transcribe!,") }

    /** What the .xsc TEXT keeps, or null if it is not one of Transcribe!. FIRST_BEAT names the bookmark of the first
     * beat, when no section or measure is there already. */
    fun read(text: String, firstBeat: String = "First beat"): File? {
        if (!looksLike(text)) return null
        val all = sections(text)
        val sound = value(all["Main"].orEmpty(), "SoundFileName")?.firstOrNull().orEmpty()
        val recording = sound.substringAfterLast('/').substringAfterLast('\\')
        val session = Session()
        val view = all["View0"].orEmpty()

        // La velocidad (100000 es 1×) y la afinación (centésimas de semitono)
        // (como Engine.clampSpeed, sin cargar el motor: también en las pruebas)
        number(value(view, "FX_Speed"), 2)?.takeIf { it > 0 }?.let {
            session.speed = (Math.round(it / 100) / 1000.0).coerceIn(Engine.MIN_SPEED, Engine.MAX_SPEED)
        }
        number(value(view, "FX_Tuning"), 4)?.takeIf { it != 0.0 }?.let {
            session.pitch = (it / 100).coerceIn(-Engine.MAX_PITCH, Engine.MAX_PITCH)
        }

        // La selección (o, vacía, el punto donde se estaba)
        val start = value(view, "SelectionLeft")?.lastOrNull()?.let(::seconds)
        val end = value(view, "SelectionRight")?.lastOrNull()?.let(::seconds)
        if (start != null && end != null && end - start >= 0.01) session.selection = round3(start) to round3(end)
        if (start != null) session.position = round3(start)

        // Los marcadores: las secciones, los compases con nombre; los pulsos dan el primero y el tempo
        val beats = mutableListOf<Double>()
        var hasBeats = false
        var sectionsSeen = 0
        for (f in all["Markers"].orEmpty()) {
            if (f[0] !in setOf("S", "M", "B") || f.size < 4) continue
            val time = f.asReversed().firstNotNullOfOrNull(::seconds) ?: continue
            val automatic = f[2] == "1"
            val name = f[3].trim()
            if (f[0] == "S") {
                session.bookmarks += Bookmark(round3(time), name.ifEmpty { if (automatic) letters(sectionsSeen) else "" })
                sectionsSeen += 1
            } else if (f[0] == "M" && name.isNotEmpty()) {
                session.bookmarks += Bookmark(round3(time), name)
            }
            if (f[0] == "M" || f[0] == "B") {
                beats += time
                hasBeats = hasBeats || f[0] == "B"
            }
        }
        beats.sort()
        if (hasBeats && beats.size > 1) {
            val intervals = beats.zipWithNext { a, b -> b - a }.filter { it > 0.05 }.sorted()
            if (intervals.isNotEmpty()) {
                val middle = intervals.size / 2
                val median = if (intervals.size % 2 == 1) intervals[middle] else (intervals[middle - 1] + intervals[middle]) / 2
                val bpm = Math.round(60.0 / median).toInt().coerceIn(CountInRules.MIN_BPM, CountInRules.MAX_BPM)
                val first = round3(beats[0])
                val same = session.bookmarks.firstOrNull { kotlin.math.abs(it.time - first) < 0.001 }
                if (same != null) same.firstBeat = true else session.bookmarks += Bookmark(first, firstBeat, true)
                session.countIn = CountIn(false, bpm, 0.0)
            }
        }
        session.bookmarks.sortBy { it.time }

        // Los bucles guardados: L,número,…,nombre,color,…,principio,final
        for (f in all["Loops"].orEmpty()) {
            if (f[0] != "L" || f.size < 4) continue
            val a = seconds(f[f.size - 2]) ?: continue
            val b = seconds(f[f.size - 1]) ?: continue
            if (b - a < 0.01) continue
            session.loops += Loop(round3(a), round3(b), f.getOrNull(5)?.trim().orEmpty())
        }
        return File(recording, session)
    }
}
