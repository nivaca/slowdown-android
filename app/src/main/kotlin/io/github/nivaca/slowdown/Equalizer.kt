// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * El ecualizador, del lado de la ventana: sus cinco bandas, la curva que pasa
 * por ellas (la misma que aplica el motor, equalizer.cpp, para dibujarla) y los
 * ajustes de py-slowdown para la música. Los ajustes propios del usuario se
 * guardan por su nombre en las preferencias, en una línea cada uno.
 */
package io.github.nivaca.slowdown

import kotlin.math.ln
import kotlin.math.max

object Equalizer {
    val BANDS = listOf(60, 250, 1000, 4000, 12000)  // Hz
    const val MAX_GAIN = 12  // dB, arriba o abajo
    val FLAT = listOf(0, 0, 0, 0, 0)

    /** GAINS as five whole decibels from -MAX_GAIN to +MAX_GAIN (missing ones, 0). */
    fun clamp(gains: List<Int>): List<Int> =
        (gains.take(BANDS.size) + List(maxOf(0, BANDS.size - gains.size)) { 0 }).map { it.coerceIn(-MAX_GAIN, MAX_GAIN) }

    fun isFlat(gains: List<Int>) = gains.all { it == 0 }

    private fun log2(x: Double) = ln(x) / ln(2.0)

    /** The gain in decibels at FREQ of the curve through GAINS: straight between two bands (on the logarithm of
     * the frequency), level beyond the first and the last. */
    fun response(gains: List<Int>, freq: Double): Double {
        val where = log2(max(freq, 1.0))
        if (where <= log2(BANDS.first().toDouble())) return gains.first().toDouble()
        for (i in 1 until BANDS.size) {
            val a = log2(BANDS[i - 1].toDouble())
            val b = log2(BANDS[i].toDouble())
            if (where <= b) return gains[i - 1] + (where - a) / (b - a) * (gains[i] - gains[i - 1])
        }
        return gains.last().toDouble()
    }

    /** The presets of py-slowdown, for music: (the key of its name in the strings, the five gains). */
    val PRESETS = listOf(
        "eq_flat" to FLAT,
        "eq_bass_up" to listOf(6, 3, 0, 0, 0),
        "eq_bass_line" to listOf(6, 3, -3, -6, -9),
        "eq_voice" to listOf(-3, -1, 3, 3, 0),
        "eq_bright" to listOf(0, 0, 0, 3, 6),
        "eq_warm" to listOf(3, 2, 0, -2, -4),
        "eq_hiss" to listOf(0, 0, 0, -2, -8),
    )

    /** The user's presets, as the preferences keep them: a line each, "name<TAB>g1 g2 g3 g4 g5". */
    fun readPresets(text: String?): List<Pair<String, List<Int>>> =
        text.orEmpty().lines().mapNotNull { line ->
            val name = line.substringBefore('\t', "").trim()
            val gains = line.substringAfter('\t', "").trim().split(' ').mapNotNull(String::toIntOrNull)
            if (name.isEmpty() || gains.size != BANDS.size) null else name to clamp(gains)
        }

    fun writePresets(presets: List<Pair<String, List<Int>>>): String =
        presets.joinToString("\n") { (name, gains) ->
            "${name.replace('\t', ' ').replace('\n', ' ').trim()}\t${gains.joinToString(" ")}"
        }

    /** PRESETS with NAME set to GAINS (replacing one of that name), in the order of their names. */
    fun withPreset(presets: List<Pair<String, List<Int>>>, name: String, gains: List<Int>) =
        (presets.filter { it.first != name } + (name to clamp(gains))).sortedBy { it.first.lowercase() }
}
