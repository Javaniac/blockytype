package com.javaniac.blockytype.profile

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileDataTest {
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val day = 86_400_000L

    private fun row(mode: String, wpm: Double, acc: Double = 97.0, seconds: Double = 30.0, at: Long = 0, raw: Double = wpm + 5, cons: Double = 80.0) =
        ResultRow(mode, wpm, raw, acc, cons, seconds, at)

    @Test
    fun `the mode key drops the language`() {
        assertEquals("words 25", ProfileBuilder.modeKey("words 25 english"))
        assertEquals("time 60", ProfileBuilder.modeKey("time 60 german_2k"))
        assertEquals("words 10", ProfileBuilder.modeKey("words 10"))
    }

    @Test
    fun `personal bests are the best wpm per mode over all languages`() {
        val p = ProfileBuilder.build(
            id, "Alice", joined = 5, face = null,
            results = listOf(
                row("words 25 english", 100.0, at = 1), row("words 25 english", 120.0, acc = 99.0, at = 2),
                row("words 25 german", 110.0, at = 3), row("time 15 english", 90.0, at = 4),
            ),
        )
        assertEquals(120.0, p.bests["words 25"]!!.wpm)
        assertEquals(99.0, p.bests["words 25"]!!.accuracy)
        assertEquals(2L, p.bests["words 25"]!!.at)
        assertEquals(90.0, p.bests["time 15"]!!.wpm)
        assertNull(p.bests["time 60"])
    }

    @Test
    fun `only the eight standard modes get a personal best`() {
        val p = ProfileBuilder.build(id, "Alice", 0, listOf(row("words 7 english", 100.0), row("time 45 english", 100.0)), null)
        assertTrue(p.bests.isEmpty())
        assertEquals(listOf("time 15", "time 30", "time 60", "time 120", "words 10", "words 25", "words 50", "words 100"), ProfileBuilder.PB_MODES)
    }

    @Test
    fun `totals and recent tests`() {
        val rows = (1..12).map { row("words 10 english", 50.0 + it, seconds = 10.0, at = it.toLong()) }
        val p = ProfileBuilder.build(id, "Alice", 0, rows, null)
        assertEquals(12, p.completed)
        assertEquals(120.0, p.seconds)
        assertEquals(12, p.recent.size)
        assertEquals(12L, p.recent.first().at) // newest first
        assertEquals(1L, p.recent.last().at)
    }

    @Test
    fun `no results is an empty profile`() {
        val p = ProfileBuilder.build(id, "Bob", 7, emptyList(), null)
        assertEquals(0, p.completed)
        assertEquals(0.0, p.seconds)
        assertTrue(p.bests.isEmpty() && p.recent.isEmpty())
        assertEquals(1, p.level.level)
        assertEquals(0L, p.level.into)
    }

    @Test
    fun `xp grows with time and accuracy and levels rise`() {
        assertTrue(Level.xp(row("words 10 english", 80.0, acc = 100.0, seconds = 60.0)) > Level.xp(row("words 10 english", 80.0, acc = 50.0, seconds = 60.0)))
        assertTrue(Level.xp(row("words 10 english", 80.0, seconds = 120.0)) > Level.xp(row("words 10 english", 80.0, seconds = 20.0)))
        var last = Level.of(0)
        assertEquals(1, last.level)
        for (xp in listOf(50L, 500L, 5_000L, 50_000L, 500_000L)) {
            val l = Level.of(xp)
            assertTrue(l.level >= last.level, "$xp")
            assertTrue(l.into in 0 until l.toNext, "$xp: ${l.into}/${l.toNext}")
            last = l
        }
        // level boundaries are exact
        val boundary = Level.totalFor(5)
        assertEquals(5, Level.of(boundary).level)
        assertEquals(0L, Level.of(boundary).into)
        assertEquals(4, Level.of(boundary - 1).level)
    }

    @Test
    fun `durations`() {
        assertEquals("28:31:18", ProfileFormat.duration(28 * 3600.0 + 31 * 60 + 18))
        assertEquals("00:00:00", ProfileFormat.duration(0.0))
        assertEquals("00:01:05", ProfileFormat.duration(65.4))
        assertEquals("100:00:00", ProfileFormat.duration(360_000.0))
    }

    @Test
    fun `dates and relative times`() {
        assertEquals("25 May 2021", ProfileFormat.date(1621900800000L)) // 2021-05-25T00:00:00Z
        val now = 100 * day
        assertEquals("now", ProfileFormat.ago(now, now - 20_000))
        assertEquals("5m", ProfileFormat.ago(now, now - 5 * 60_000))
        assertEquals("3h", ProfileFormat.ago(now, now - 3 * 3_600_000))
        assertEquals("2d", ProfileFormat.ago(now, now - 2 * day))
        assertEquals("3w", ProfileFormat.ago(now, now - 21 * day))
        assertEquals("4mo", ProfileFormat.ago(now, now - 125 * day - 0))
        assertEquals("now", ProfileFormat.ago(now, now + day)) // clock skew never shows the future
    }

    @Test
    fun `big numbers are shortened`() {
        assertEquals("999", ProfileFormat.compact(999))
        assertEquals("5.0k", ProfileFormat.compact(5_000))
        assertEquals("6.7k", ProfileFormat.compact(6_700))
        assertEquals("12k", ProfileFormat.compact(12_345))
        assertEquals("1.2m", ProfileFormat.compact(1_234_567))
    }
}
