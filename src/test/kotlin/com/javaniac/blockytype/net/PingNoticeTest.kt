package com.javaniac.blockytype.net

import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PingNoticeTest {
    private fun snap(median: Double, count: Int = 20) = RttTracker.Snapshot(count, median, median, median, 0.0)

    @Test
    fun `no samples means no warning`() = assertFalse(PingNotice.warns(null))

    @Test
    fun `too few samples means no warning`() = assertFalse(PingNotice.warns(snap(500.0, count = PingNotice.MIN_SAMPLES - 1)))

    @Test
    fun `exactly 100 ms is fine`() = assertFalse(PingNotice.warns(snap(100.0)))

    @Test
    fun `above 100 ms warns`() = assertTrue(PingNotice.warns(snap(100.5)))

    @Test
    fun `one spike does not warn when the median is low`() =
        assertFalse(PingNotice.warns(RttTracker.Snapshot(20, 40.0, 30.0, 900.0, 10.0)))

    @Test
    fun `message names the ping and the effect`() {
        val text = PlainTextComponentSerializer.plainText().serialize(PingNotice.message(DefaultTheme, 143.4))
        assertEquals("» High ping (143 ms): your experience may be worse.", text)
    }
}
