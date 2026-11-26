// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Las grabaciones abiertas hace poco, para la pantalla de inicio (como la de
 * py-slowdown): sin un archivo abierto, la onda las muestra, cada una con una
 * figura pequeña de su onda. La figura es un resumen de COLUMNS columnas (el
 * pico de cada una, de 0 a 1), que se guarda con la dirección y el nombre en
 * las preferencias, una línea por grabación: dirección, tabulador, nombre,
 * tabulador, y las columnas en hexadecimal, dos cifras cada una.
 */
package io.github.nivaca.slowdown

data class Recent(val uri: String, val name: String, val summary: FloatArray) {
    override fun equals(other: Any?) =
        other is Recent && other.uri == uri && other.name == name && other.summary.contentEquals(summary)

    override fun hashCode() = uri.hashCode() * 31 + name.hashCode()
}

object Recents {
    const val COLUMNS = 120
    const val KEEP = 8

    fun read(text: String?): List<Recent> = text.orEmpty().lines().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 2 || parts[0].isEmpty()) return@mapNotNull null
        val hex = parts.getOrNull(2).orEmpty()
        val summary = FloatArray(COLUMNS) { i ->
            hex.substring(minOf(hex.length, 2 * i), minOf(hex.length, 2 * i + 2)).toIntOrNull(16)?.let { it / 255f } ?: 0f
        }
        Recent(parts[0], parts[1], summary)
    }.take(KEEP)

    fun write(recents: List<Recent>): String = recents.take(KEEP).joinToString("\n") { r ->
        val hex = r.summary.joinToString("") { "%02x".format(Math.round(it.coerceIn(0f, 1f) * 255)) }
        "${r.uri}\t${r.name.replace('\t', ' ').replace('\n', ' ')}\t$hex"
    }

    /** RECENTS with RECENT first (instead of where it was); its summary, if empty, the one it had. */
    fun opened(recents: List<Recent>, recent: Recent): List<Recent> {
        val before = recents.firstOrNull { it.uri == recent.uri }
        val kept = if (recent.summary.all { it == 0f } && before != null) recent.copy(summary = before.summary) else recent
        return (listOf(kept) + recents.filter { it.uri != recent.uri }).take(KEEP)
    }

    /** The summary of a file from the minimum and maximum of each of COLUMNS columns (PEAKS, as Engine.peaks gives
     * them), at the scale of the loudest. */
    fun summary(peaks: FloatArray): FloatArray {
        val n = peaks.size / 2
        val out = FloatArray(n) { maxOf(kotlin.math.abs(peaks[2 * it]), kotlin.math.abs(peaks[2 * it + 1])) }
        val loudest = out.maxOrNull()?.takeIf { it > 0 } ?: return out
        for (i in out.indices) out[i] /= loudest
        return out
    }
}
