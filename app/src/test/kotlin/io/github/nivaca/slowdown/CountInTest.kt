// SPDX-License-Identifier: GPL-3.0-or-later
// La cuenta previa: las reglas de py-slowdown (countin.py).
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountInTest {
    @Test
    fun theTempoFollowsTheSpeed() {
        assertEquals(60.0, CountInRules.tempo(120, 0.5, follow = true), 1e-9)
        assertEquals(120.0, CountInRules.tempo(120, 0.5, follow = false), 1e-9)
        assertEquals(20.0, CountInRules.tempo(30, 0.25, follow = true), 1e-9)  // (no baja de 20)
    }

    @Test
    fun theNearestBeatOfTheFirstBeat() {
        // Primer pulso en 1,0 s, 120 BPM (un pulso cada 0,5 s)
        assertEquals(0.0, CountInRules.gridOffset(3.0, 1.0, 120.0, 1.0, 0.4), 1e-9)
        assertEquals(0.1, CountInRules.gridOffset(3.1, 1.0, 120.0, 1.0, 0.4), 1e-9)  // entra después del pulso
        assertEquals(-0.1, CountInRules.gridOffset(2.9, 1.0, 120.0, 1.0, 0.4), 1e-9)  // antes
        // Si no puede entrar tan antes, toma el pulso anterior
        assertEquals(0.4, CountInRules.gridOffset(2.9, 1.0, 120.0, 1.0, 0.05), 1e-9)
        // Antes del primer pulso también hay rejilla
        assertEquals(0.0, CountInRules.gridOffset(0.0, 1.0, 120.0, 1.0, 0.4), 1e-9)
    }

    @Test
    fun theTempoOfTheRecording() {
        assertEquals(120, CountInRules.recordingTempo(90.0, 0.75, follow = true))
        assertEquals(90, CountInRules.recordingTempo(90.0, 0.75, follow = false))
    }

    @Test
    fun tapping() {
        val tap = TapTempo()
        assertNull(tap.tap(10.0))
        assertEquals(120.0, tap.tap(10.5)!!, 1e-9)
        tap.tap(11.0)
        assertEquals(120.0, tap.tap(11.7)!!, 1e-9)  // (un golpe desparejo no mueve la mediana)
        tap.tap(12.2)
        assertEquals(5, tap.taps)
        // Una pausa larga empieza de nuevo
        assertNull(tap.tap(20.0))
        assertEquals(1, tap.taps)
    }
}
