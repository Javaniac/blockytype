package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResultCursorTest {
    @Test
    fun `starts unset and the first step picks an end`() {
        val c = ResultCursor()
        assertNull(c.index)
        c.step(+1, 10)
        assertEquals(0, c.index)
        val d = ResultCursor()
        d.step(-1, 10)
        assertEquals(9, d.index)
    }

    @Test
    fun `moves one second at a time and clamps at the ends`() {
        val c = ResultCursor()
        repeat(3) { c.step(+1, 3) }
        assertEquals(2, c.index)
        c.step(+1, 3)
        assertEquals(2, c.index)
        repeat(5) { c.step(-1, 3) }
        assertEquals(0, c.index)
    }

    @Test
    fun `reset clears it and a shorter result clamps it`() {
        val c = ResultCursor()
        repeat(5) { c.step(+1, 10) }
        c.step(-1, 3)
        assertEquals(2, c.index)
        c.reset()
        assertNull(c.index)
    }

    @Test
    fun `an empty result has no cursor`() {
        val c = ResultCursor()
        c.step(+1, 0)
        assertNull(c.index)
    }
}
