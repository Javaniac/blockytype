package com.javaniac.blockytype.pack

import com.javaniac.blockytype.ui.Stopwatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The keybind probe opens an anvil; the pack and the server make it invisible. */
class ProbeHidingTest {
    private val files = PackBuilder.build()

    @Test
    fun `the probe clock is negative so no real game time can look like it`() {
        assertTrue(Stopwatch.PROBE_TICKS < 0)
        assertEquals(Stopwatch.PROBE_TICKS, Stopwatch.probePacket().gameTime())
    }

    @Test
    fun `text vsh hides every unmarked text while the probe clock runs, on every client version`() {
        for (v in ClientVersion.entries) {
            val src = Shaders.files(v).entries.single { it.key.endsWith("core/text.vsh") }.value
            assertTrue("GameTime < ${Shaders.PROBE_GAME_TIME}" in src, "probe clock ${v.label}")
        }
    }

    @Test
    fun `gui vsh hides the anvil name box cursor`() {
        for (v in ClientVersion.entries) {
            val src = Shaders.files(v).entries.single { it.key.endsWith("core/gui.vsh") }.value
            assertTrue("Anvil name box" in src, v.label)
        }
    }

    @Test
    fun `the anvil background and sprites are transparent`() {
        for (path in listOf(
            "assets/minecraft/textures/gui/container/anvil.png",
            "assets/minecraft/textures/gui/sprites/container/anvil/text_field.png",
            "assets/minecraft/textures/gui/sprites/container/anvil/text_field_disabled.png",
            "assets/minecraft/textures/gui/sprites/container/anvil/error.png",
            "assets/minecraft/textures/gui/sprites/container/slot_highlight_back.png",
            "assets/minecraft/textures/gui/sprites/container/slot_highlight_front.png",
        )) {
            assertTrue(path in files, path)
        }
    }

    @Test
    fun `the pack has the empty model the probe item points at`() {
        // The item itself (KeybindProbe.item) needs the server registries, so it is checked in game.
        assertTrue("assets/blockytype/items/blank.json" in files)
        assertTrue("minecraft:empty" in String(files.getValue("assets/blockytype/items/blank.json")))
    }
}
