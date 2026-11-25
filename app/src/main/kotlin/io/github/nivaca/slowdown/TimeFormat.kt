// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Los tiempos como se muestran: minutos y segundos (1:23.4), con horas si hacen
 * falta (1:02:03.4).
 */
package io.github.nivaca.slowdown

import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

object TimeFormat {
    /** SECONDS as m:ss.d (or h:mm:ss.d), with tenths of a second. */
    fun long(seconds: Double): String {
        val tenths = (maxOf(0.0, seconds) * 10).roundToLong()
        val h = tenths / 36000
        val m = tenths / 600 % 60
        val s = tenths / 10 % 60
        val d = tenths % 10
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d.%d", h, m, s, d)
        else String.format(Locale.ROOT, "%d:%02d.%d", m, s, d)
    }

    /** SECONDS for the ruler, with decimals only if its marks are less than a second apart (STEP). */
    fun short(seconds: Double, step: Double): String {
        val s = maxOf(0.0, seconds)
        if (step >= 1.0) {
            val whole = (s + 1e-6).toLong()
            val h = whole / 3600
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, whole / 60 % 60, whole % 60)
            else String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60)
        }
        val decimals = if (step >= 0.1) 1 else if (step >= 0.01) 2 else 3
        val minutes = floor((s + 1e-9) / 60).toLong()
        val rest = s - minutes * 60
        return String.format(Locale.ROOT, "%d:%0${3 + decimals}.${decimals}f", minutes, rest)
    }
}
