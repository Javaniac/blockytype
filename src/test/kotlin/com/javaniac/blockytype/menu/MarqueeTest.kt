package com.javaniac.blockytype.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class MarqueeTest {
    private val step = Marquee.STEP_NANOS

    @Test
    fun `text that fits never moves`() {
        assertEquals(0, Marquee.offset(50, 50, 100 * step))
        assertEquals(0, Marquee.offset(10, 50, 100 * step))
    }

    @Test
    fun `starts with a pause`() {
        assertEquals(0, Marquee.offset(120, 50, 0))
        assertEquals(0, Marquee.offset(120, 50, (Marquee.HOLD_STEPS - 1) * step))
    }

    @Test
    fun `moves one char per step and stops at the end`() {
        assertEquals(1, Marquee.offset(120, 50, (Marquee.HOLD_STEPS + 1) * step))
        assertEquals(70, Marquee.offset(120, 50, (Marquee.HOLD_STEPS + 70) * step))
        assertEquals(70, Marquee.offset(120, 50, (Marquee.HOLD_STEPS + 75) * step))
    }

    @Test
    fun `wraps back to the start`() {
        val cycle = (70 + 2 * Marquee.HOLD_STEPS) * step
        assertEquals(0, Marquee.offset(120, 50, cycle))
        assertEquals(1, Marquee.offset(120, 50, cycle + (Marquee.HOLD_STEPS + 1) * step))
    }
}
