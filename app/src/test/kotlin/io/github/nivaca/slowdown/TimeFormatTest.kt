// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.nivaca.slowdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun long() {
        assertEquals("0:00.0", TimeFormat.long(0.0))
        assertEquals("1:23.4", TimeFormat.long(83.44))
        assertEquals("1:00.0", TimeFormat.long(59.96))
        assertEquals("1:02:03.5", TimeFormat.long(3723.5))
        assertEquals("0:00.0", TimeFormat.long(-3.0))
    }

    @Test
    fun ruler() {
        assertEquals("1:05", TimeFormat.short(65.0, 5.0))
        assertEquals("1:00:00", TimeFormat.short(3600.0, 60.0))
        assertEquals("0:01.5", TimeFormat.short(1.5, 0.5))
        assertEquals("1:05.25", TimeFormat.short(65.25, 0.05))
    }
}
