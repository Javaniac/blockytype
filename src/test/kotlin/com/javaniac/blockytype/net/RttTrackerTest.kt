package com.javaniac.blockytype.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RttTrackerTest {
    @Test
    fun `empty tracker has no numbers`() {
        val t = RttTracker()
        assertEquals(0, t.count())
        assertNull(t.snapshot())
    }

    @Test
    fun `median min max and jitter of a few samples`() {
        val t = RttTracker()
        listOf(40.0, 50.0, 60.0, 50.0, 100.0).forEach(t::add)
        val s = t.snapshot()!!
        assertEquals(5, s.count)
        assertEquals(50.0, s.median, 0.0001)
        assertEquals(40.0, s.min, 0.0001)
        assertEquals(100.0, s.max, 0.0001)
        // mean 60, mean absolute deviation (20 + 10 + 0 + 10 + 40) / 5 = 16
        assertEquals(16.0, s.jitter, 0.0001)
    }

    @Test
    fun `even count median is the mean of the middle two`() {
        val t = RttTracker()
        listOf(10.0, 20.0, 30.0, 40.0).forEach(t::add)
        assertEquals(25.0, t.snapshot()!!.median, 0.0001)
    }

    @Test
    fun `only the last samples count`() {
        val t = RttTracker(window = 3)
        listOf(500.0, 10.0, 20.0, 30.0).forEach(t::add)
        val s = t.snapshot()!!
        assertEquals(3, s.count)
        assertEquals(10.0, s.min, 0.0001)
        assertEquals(20.0, s.median, 0.0001)
    }

    @Test
    fun `steady connection has zero jitter`() {
        val t = RttTracker()
        repeat(10) { t.add(80.0) }
        assertEquals(0.0, t.snapshot()!!.jitter, 0.0001)
    }

    @Test
    fun `negative samples are ignored`() {
        val t = RttTracker()
        t.add(-1.0)
        t.add(Double.NaN)
        assertEquals(0, t.count())
    }

    @Test
    fun `reset clears the window`() {
        val t = RttTracker()
        t.add(10.0)
        t.reset()
        assertNull(t.snapshot())
    }
}
