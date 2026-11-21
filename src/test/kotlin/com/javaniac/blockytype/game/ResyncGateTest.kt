package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResyncGateTest {
    private val ms = 1_000_000L

    @Test
    fun `open gate passes everything`() {
        val g = ResyncGate()
        assertEquals(ResyncGate.Verdict.PASS, g.check("abc", 0))
    }

    @Test
    fun `after a resync stale edits are dropped and the echo closes the gate`() {
        val g = ResyncGate()
        g.expect("the qu", 0)
        assertEquals(ResyncGate.Verdict.DROP, g.check("the qx", 10 * ms))
        assertEquals(ResyncGate.Verdict.DROP, g.check("th", 20 * ms))
        assertEquals(ResyncGate.Verdict.ECHO, g.check("the qu", 30 * ms))
        assertFalse(g.waiting)
        assertEquals(ResyncGate.Verdict.PASS, g.check("the que", 40 * ms))
    }

    @Test
    fun `a key typed on the rewritten text passes even without an echo`() {
        val g = ResyncGate()
        g.expect("the qu", 0)
        assertEquals(ResyncGate.Verdict.PASS, g.check("the qui", 10 * ms))
        assertFalse(g.waiting)
        g.expect("the qu", 20 * ms)
        assertEquals(ResyncGate.Verdict.PASS, g.check("the q", 30 * ms))
        assertFalse(g.waiting)
    }

    @Test
    fun `the gate times out so a missing echo cannot freeze typing`() {
        val g = ResyncGate()
        g.expect("the", 0)
        assertEquals(ResyncGate.Verdict.DROP, g.check("thx", 500 * ms))
        assertEquals(ResyncGate.Verdict.PASS, g.check("thx", ResyncGate.TIMEOUT_NANOS + 1))
        assertFalse(g.waiting)
    }

    @Test
    fun `a new resync restarts the wait`() {
        val g = ResyncGate()
        g.expect("abc", 0)
        g.expect("abcd", 100 * ms)
        assertTrue(g.waiting)
        assertEquals(ResyncGate.Verdict.DROP, g.check("ab", 110 * ms))
        assertEquals(ResyncGate.Verdict.ECHO, g.check("abcd", 120 * ms))
    }

    @Test
    fun `reset opens the gate`() {
        val g = ResyncGate()
        g.expect("a", 0)
        g.reset()
        assertEquals(ResyncGate.Verdict.PASS, g.check("z", 1))
    }
}
