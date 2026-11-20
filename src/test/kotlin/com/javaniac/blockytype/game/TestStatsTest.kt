package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestStatsTest {
    @Test
    fun `wpm formula`() {
        assertEquals(60.0, TestStats.wpm(50, 10.0), 1e-9) // 50 chars / 5 / (10 / 60)
        assertEquals(0.0, TestStats.wpm(50, 0.0))
    }

    @Test
    fun `kogasa`() {
        assertEquals(100.0, TestStats.kogasa(0.0), 1e-9)
        assertTrue(TestStats.kogasa(1.0) < 50.0)
    }

    @Test
    fun `consistency of equal bursts is 100`() {
        assertEquals(100.0, TestStats.consistency(listOf(50.0, 50.0, 50.0)), 1e-9)
        assertEquals(0.0, TestStats.consistency(emptyList()))
    }

    @Test
    fun `countChars classes`() {
        val target = listOf("hello", "world")
        // "helo" is short (missed 1), "worldd" has an extra letter
        val c = TestStats.countChars("helo worldd", target)
        assertEquals(1, c.missed)
        assertEquals(1, c.extra)
        // h e l matched, o vs l wrong; word 2: 5 matched + extra
        assertEquals(3 + 5, c.correct)
        assertEquals(1, c.incorrect)
        assertEquals(0, c.correctWordChars)
    }

    @Test
    fun `correct words count with their space`() {
        val c = TestStats.countChars("hello world", listOf("hello", "world"))
        assertEquals(11, c.correct)
        assertEquals(11, c.correctWordChars) // 5 + space + 5
        assertEquals(11, c.rawChars)
    }
}
