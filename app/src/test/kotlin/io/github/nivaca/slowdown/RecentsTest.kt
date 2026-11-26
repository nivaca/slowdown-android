// SPDX-License-Identifier: GPL-3.0-or-later
// Las grabaciones de hace poco (la pantalla de inicio): cómo se guardan, y en qué orden.
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    private fun recent(n: Int, level: Float = 0f) = Recent("content://x/$n", "song $n.mp3", FloatArray(Recents.COLUMNS) { level })

    @Test
    fun writtenAndReadAgain() {
        val summary = FloatArray(Recents.COLUMNS) { it / (Recents.COLUMNS - 1f) }
        val list = listOf(Recent("content://a/b%20c", "b c.mp3", summary), recent(2))
        val back = Recents.read(Recents.write(list))
        assertEquals(list.map { it.uri to it.name }, back.map { it.uri to it.name })
        for (i in summary.indices) assertEquals(summary[i], back[0].summary[i], 1f / 255)
        assertEquals(0, Recents.read(null).size)
    }

    @Test
    fun theLastOpenedFirst() {
        var list = (1..Recents.KEEP).map { recent(it, 0.5f) }
        list = Recents.opened(list, recent(3))  // (vuelve sin figura: se queda con la que tenía)
        assertEquals("content://x/3", list[0].uri)
        assertEquals(0.5f, list[0].summary[0], 1e-6f)
        list = Recents.opened(list, recent(99))
        assertEquals(Recents.KEEP, list.size)
        assertEquals("content://x/99", list[0].uri)
    }

    @Test
    fun aSummaryAtTheScaleOfTheLoudest() {
        val peaks = floatArrayOf(-0.1f, 0.2f, -0.5f, 0.25f, 0f, 0f)
        assertEquals(listOf(0.4f, 1f, 0f), Recents.summary(peaks).toList())
    }
}
