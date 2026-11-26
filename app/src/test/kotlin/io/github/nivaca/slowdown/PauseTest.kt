// SPDX-License-Identifier: GPL-3.0-or-later
// La pausa, como en py-slowdown: adónde vuelve la posición.
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseTest {
    @Test
    fun aPauseGoesBackToWherePlayingStarted() {
        assertEquals(12.5, Playback.returnPoint(12.5, null)!!, 1e-9)
        assertNull(Playback.returnPoint(null, null))  // (nada tocado todavía: se queda donde está)
        assertNull(Playback.returnPoint(null, 2.0 to 5.0))
    }

    @Test
    fun withARepeatedSelectionItStaysInsideIt() {
        assertEquals(3.0, Playback.returnPoint(3.0, 2.0 to 5.0)!!, 1e-9)
        assertEquals(2.0, Playback.returnPoint(2.0, 2.0 to 5.0)!!, 1e-9)
        // Empezó antes de la selección, o en su final, o después: vuelve a su principio
        assertEquals(2.0, Playback.returnPoint(1.0, 2.0 to 5.0)!!, 1e-9)
        assertEquals(2.0, Playback.returnPoint(5.0, 2.0 to 5.0)!!, 1e-9)
        assertEquals(2.0, Playback.returnPoint(9.0, 2.0 to 5.0)!!, 1e-9)
    }
}
