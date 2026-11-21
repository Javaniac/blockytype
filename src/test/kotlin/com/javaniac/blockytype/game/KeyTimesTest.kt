package com.javaniac.blockytype.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyTimesTest {
    private fun keys(vararg ms: Long) = ms.mapIndexed { i, t -> KeyPress(t, i % 2 == 0, i / 3) }

    @Test
    fun `regular typing is untouched`() {
        val k = keys(0, 90, 180, 300, 400, 520, 640)
        assertEquals(k, KeyTimes.deburst(k))
    }

    @Test
    fun `burst after a gap is spread between the previous key and the last key of the run`() {
        // 0, 100, then a 600 ms lag, then 4 keys released within 4 ms of each other: (704 - 100) / 4 = 151 per key
        val k = keys(0, 100, 700, 701, 702, 704)
        val out = KeyTimes.deburst(k).map { it.ms }
        assertEquals(listOf(0L, 100L, 251L, 402L, 553L, 704L), out)
    }

    @Test
    fun `last key, first key, count and flags are preserved`() {
        val k = keys(0, 100, 700, 701, 702, 703)
        val out = KeyTimes.deburst(k)
        assertEquals(k.size, out.size)
        assertEquals(k.first().ms, out.first().ms)
        assertEquals(k.last().ms, out.last().ms)
        assertEquals(k.map { it.correct }, out.map { it.correct })
        assertEquals(k.map { it.word }, out.map { it.word })
    }

    @Test
    fun `times never go backwards`() {
        val k = keys(0, 100, 700, 700, 700, 700, 1500, 1501, 1501, 1502)
        val out = KeyTimes.deburst(k).map { it.ms }
        assertTrue(out.zipWithNext().all { (a, b) -> a <= b }, out.toString())
    }

    @Test
    fun `a short run of two keys is left alone`() {
        val k = keys(0, 100, 700, 702)
        assertEquals(k, KeyTimes.deburst(k))
    }

    @Test
    fun `a burst without a long gap before it is left alone`() {
        val k = keys(0, 100, 120, 122, 124)
        assertEquals(k, KeyTimes.deburst(k))
    }

    @Test
    fun `empty and single key lists work`() {
        assertEquals(emptyList(), KeyTimes.deburst(emptyList()))
        val one = keys(0)
        assertEquals(one, KeyTimes.deburst(one))
    }
}
