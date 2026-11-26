// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La cuenta previa: unos golpes a un tempo antes de que empiece a sonar la
 * grabación, como en py-slowdown (countin.py). Aquí están sus reglas, sin
 * nada de Android: el tempo de los golpes a una velocidad, el tempo marcado
 * por el usuario con el pulso de la música, y dónde entra la grabación cuando
 * el archivo tiene un primer pulso (en el pulso más cercano de su rejilla).
 */
package io.github.nivaca.slowdown

import kotlin.math.roundToLong

object CountInRules {
    const val MIN_BPM = 20
    const val MAX_BPM = 300
    const val MAX_BEATS = 16
    const val MAX_OFFSET = 2.0  // segundos que la grabación puede entrar antes o después del pulso

    fun clampBpm(bpm: Double) = bpm.coerceIn(MIN_BPM.toDouble(), MAX_BPM.toDouble())

    /** The tempo of the clicks at SPEED: with FOLLOW, 120 BPM at 0.5× are counted at 60. */
    fun tempo(bpm: Int, speed: Double, follow: Boolean) = clampBpm(if (follow) bpm * speed else bpm.toDouble())

    /** The offset of the count-in (seconds of the recording) playing from POSITION, with the recording's first
     * beat at FIRST and its tempo BPM: the recording comes in on the nearest beat of FIRST, FIRST ± one beat…
     * It can come in at most EARLIEST seconds (heard, at SPEED) before the beat; if the nearest asks for more,
     * the one before it is taken. */
    fun gridOffset(position: Double, first: Double, bpm: Double, speed: Double, earliest: Double): Double {
        val period = 60.0 / clampBpm(bpm)
        val beat = first + Math.round((position - first) / period) * period
        var offset = position - beat  // + entra después del pulso, - antes
        if (offset < 0 && -offset / speed > earliest + 1e-9) offset += period
        return (offset * 1000).roundToLong() / 1000.0
    }

    /** (bar, beat), from 1, of POSITION (seconds) in a recording whose first beat is at FIRST, at BPM, with PER_BAR
     * beats in a bar; null before the first beat (as py-slowdown's clock). */
    fun barAndBeat(position: Double, first: Double, bpm: Double, perBar: Int): Pair<Int, Int>? {
        if (position < first - 0.02 || bpm <= 0 || perBar < 1) return null
        val beats = (maxOf(0.0, position - first) * bpm / 60.0 + 1e-6).toInt()
        return beats / perBar + 1 to beats % perBar + 1
    }

    /** The tempo of the recording, from the tempo HEARD at SPEED: following the speed, what was tapped at 0.75×
     * is the recording's tempo times 0.75. */
    fun recordingTempo(heard: Double, speed: Double, follow: Boolean): Int =
        Math.round(clampBpm(if (follow && speed > 0) heard / speed else heard)).toInt()
}

/** The tempo of taps: the median of the intervals between the last taps (one uneven tap does not move it); a
 * pause of more than PAUSE seconds begins a new measure. */
class TapTempo {
    private val times = ArrayList<Double>()

    val taps: Int get() = times.size

    /** A tap at WHEN (seconds): the tempo in BPM so far, or null after the first tap. */
    fun tap(whenSeconds: Double): Double? {
        if (times.isNotEmpty() && whenSeconds - times.last() > PAUSE) times.clear()
        times += whenSeconds
        while (times.size > KEEP) times.removeAt(0)
        if (times.size < 2) return null
        val intervals = times.zipWithNext { a, b -> b - a }.sorted()
        val middle = intervals.size / 2
        val median = if (intervals.size % 2 == 1) intervals[middle] else (intervals[middle - 1] + intervals[middle]) / 2
        return CountInRules.clampBpm(60.0 / median)
    }

    companion object {
        const val PAUSE = 2.0
        const val KEEP = 8  // golpes medidos (siete intervalos)
    }
}
