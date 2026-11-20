package com.javaniac.blockytype.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StopwatchTest {
    @Test
    fun `reset packet sets the client game time to zero and leaves clocks alone`() {
        val p = Stopwatch.resetPacket()
        assertEquals(0L, p.gameTime())
        assertTrue(p.clocks().isEmpty())
    }
}
