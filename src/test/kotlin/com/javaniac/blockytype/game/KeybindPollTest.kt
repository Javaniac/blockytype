package com.javaniac.blockytype.game

import com.javaniac.blockytype.settings.SettingsCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeybindPollTest {
    @Test
    fun `the setting maps to an interval`() {
        assertNull(KeybindPoll.intervalMillis("off"))
        assertEquals(60_000L, KeybindPoll.intervalMillis("1 min"))
        assertEquals(300_000L, KeybindPoll.intervalMillis("5 min"))
        assertEquals(1_800_000L, KeybindPoll.intervalMillis("30 min"))
        assertEquals(KeybindPoll.DEFAULT_MILLIS, KeybindPoll.intervalMillis("bogus"))
        for (o in SettingsCatalog.byId("keybind-check")!!.options.values()) {
            if (o != "off") assertTrue((KeybindPoll.intervalMillis(o) ?: 0) >= 60_000, o)
        }
    }

    @Test
    fun `a check is due when the time is up and the player is idle`() {
        assertTrue(KeybindPoll.due(now = 1000, nextAt = 1000, idle = true))
        assertTrue(KeybindPoll.due(now = 5000, nextAt = 1000, idle = true))
        assertFalse(KeybindPoll.due(now = 999, nextAt = 1000, idle = true))
        assertFalse(KeybindPoll.due(now = 5000, nextAt = 1000, idle = false), "never interrupts a test, a menu or a result")
    }

    @Test
    fun `the next check is one interval after the last`() {
        assertEquals(61_000L, KeybindPoll.next(now = 1000, intervalMillis = 60_000))
        assertNull(KeybindPoll.next(now = 1000, intervalMillis = null))
    }

    @Test
    fun `the setting exists and starts at five minutes`() {
        val s = SettingsCatalog.byId("keybind-check")!!
        assertEquals("5 min", s.default)
        assertTrue(s.implemented)
        assertEquals(listOf("off", "1 min", "2 min", "5 min", "10 min", "30 min"), s.options.values())
    }
}
