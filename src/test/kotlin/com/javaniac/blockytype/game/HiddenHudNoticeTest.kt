package com.javaniac.blockytype.game

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiddenHudNoticeTest {
    @Test
    fun `the notice repeats the sentence`() {
        val text = PlainTextComponentSerializer.plainText().serialize(HiddenHudNotice.text())
        assertEquals(HiddenHudNotice.REPEATS, text.windowed(HiddenHudNotice.SENTENCE.length).count { it == HiddenHudNotice.SENTENCE })
        assertEquals("You have hidden the HUD. Press F1 to show it again", HiddenHudNotice.SENTENCE)
        assertTrue(HiddenHudNotice.REPEATS >= 8)
    }

    @Test
    fun `it sits between the camera and the command block`() {
        val z = HiddenHudNotice.POS.z()
        assertTrue(z > com.javaniac.blockytype.events.join.ConnectEvent.STAND.z())
        assertTrue(z < com.javaniac.blockytype.events.join.ConnectEvent.BLOCK_POS.z())
    }
}
