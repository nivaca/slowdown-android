// SPDX-License-Identifier: GPL-3.0-or-later
// El ecualizador, del lado de la ventana: la curva que se dibuja, los ajustes propios y su lugar en el .slx.
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerTest {
    @Test
    fun theCurveThroughTheFiveBands() {
        val gains = listOf(6, -3, 0, 9, -12)
        for ((i, band) in Equalizer.BANDS.withIndex()) assertEquals(gains[i].toDouble(), Equalizer.response(gains, band.toDouble()), 1e-9)
        assertEquals(-1.5, Equalizer.response(gains, 500.0), 1e-9)  // (a medio camino de 250 a 1000, sobre el logaritmo)
        assertEquals(6.0, Equalizer.response(gains, 20.0), 1e-9)
        assertEquals(-12.0, Equalizer.response(gains, 20000.0), 1e-9)
    }

    @Test
    fun clampedAndFlat() {
        assertEquals(listOf(12, -12, 3, 0, 0), Equalizer.clamp(listOf(20, -20, 3)))
        assertTrue(Equalizer.isFlat(Equalizer.FLAT))
        assertFalse(Equalizer.isFlat(listOf(0, 0, 1, 0, 0)))
        assertEquals(7, Equalizer.PRESETS.size)
    }

    @Test
    fun theUsersPresets() {
        val own = Equalizer.withPreset(Equalizer.withPreset(emptyList(), "Zeta", listOf(1, 2, 3, 4, 5)), "alfa", listOf(-1, 0, 0, 0, 1))
        assertEquals(listOf("alfa", "Zeta"), own.map { it.first })
        val again = Equalizer.readPresets(Equalizer.writePresets(own))
        assertEquals(own, again)
        // Otro con el mismo nombre lo reemplaza; una línea rota no cuenta
        assertEquals(listOf(0, 0, 0, 0, 9), Equalizer.withPreset(own, "Zeta", listOf(0, 0, 0, 0, 9)).last().second)
        assertEquals(1, Equalizer.readPresets("bien\t1 2 3 4 5\nmal\t1 2\n\tsin nombre").size)
    }

    @Test
    fun keptInTheSlxAsTheDesktopKeepsIt() {
        val source = Source("content://x/canción.mp3", "canción.mp3", 10)
        val session = Session(eq = listOf(3, 0, -2, 0, 4), eqOn = false)
        val text = Sidecar.format(session, source, "1.3")
        assertTrue(text.contains("<equalizer gains=\"3 0 -2 0 4\" on=\"false\" />"))
        val back = Sidecar.read(text)!!
        assertEquals(listOf(3, 0, -2, 0, 4), back.eq)
        assertFalse(back.eqOn)
        // Plano, no se escribe; uno de py-slowdown (sin «on») está encendido
        assertFalse(Sidecar.format(Session(), source, "1.3").contains("equalizer"))
        val desktop = text.replace(" on=\"false\"", "")
        assertTrue(Sidecar.read(desktop)!!.eqOn)
    }
}
