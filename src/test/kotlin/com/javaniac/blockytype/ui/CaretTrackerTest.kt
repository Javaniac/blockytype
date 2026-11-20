package com.javaniac.blockytype.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CaretTrackerTest {
    private val ms = 1_000_000L

    @Test
    fun `the very first step from the start of the text slides`() {
        val t = CaretTracker()
        assertEquals(CaretMove.FROM_LEFT, t.update(0 to 1, 0))
        assertEquals(CaretMove.FROM_LEFT, t.current(100 * ms))
    }

    @Test
    fun `the flag expires so the shader never replays a slide when the game time wraps`() {
        val t = CaretTracker(flagNanos = 700 * ms)
        t.update(0 to 1, 0)
        assertEquals(CaretMove.FROM_LEFT, t.current(699 * ms))
        assertEquals(CaretMove.NONE, t.current(700 * ms))
    }

    @Test
    fun `backspace slides in from the right`() {
        val t = CaretTracker()
        t.update(0 to 1, 0)
        t.update(0 to 0, 200 * ms)
        assertEquals(CaretMove.FROM_RIGHT, t.current(250 * ms))
    }

    @Test
    fun `a jump or line wrap clears the flag at once`() {
        val t = CaretTracker()
        t.update(0 to 1, 0)
        assertEquals(CaretMove.NONE, t.update(1 to 0, 100 * ms))
        assertEquals(CaretMove.NONE, t.current(110 * ms))
    }

    @Test
    fun `unchanged caret keeps the running slide`() {
        val t = CaretTracker()
        t.update(0 to 1, 0)
        assertEquals(CaretMove.NONE, t.update(0 to 1, 50 * ms)) // nothing moved, so nothing to restart
        assertEquals(CaretMove.FROM_LEFT, t.current(60 * ms))
    }

    @Test
    fun `reset seeds the start so the next first step slides again`() {
        val t = CaretTracker()
        t.update(0 to 1, 0)
        t.update(0 to 2, 10 * ms)
        t.reset()
        assertEquals(CaretMove.NONE, t.current(20 * ms))
        assertEquals(CaretMove.FROM_LEFT, t.update(0 to 1, 30 * ms))
    }
}
