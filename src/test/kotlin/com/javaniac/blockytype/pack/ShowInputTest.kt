package com.javaniac.blockytype.pack

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowInputTest {
    private fun shader(v: ClientVersion, name: String, show: Boolean) =
        Shaders.files(v, showInput = show).entries.single { it.key.endsWith("core/$name") }.value

    @Test
    fun `visible input does not hide the command box text on any client version`() {
        for (v in ClientVersion.entries) {
            assertFalse("Command box: plain text" in shader(v, "text.vsh", true), "${v.label} text.vsh")
            assertFalse("Insert cursor" in shader(v, "gui.vsh", true), "${v.label} gui.vsh")
        }
    }

    @Test
    fun `hidden input still hides box text and cursor`() {
        for (v in ClientVersion.entries) {
            assertTrue("Command box: plain text" in shader(v, "text.vsh", false), "${v.label} text.vsh")
            assertTrue("Insert cursor" in shader(v, "gui.vsh", false), "${v.label} gui.vsh")
        }
    }

    @Test
    fun `usage hint and the other screen parts stay hidden when the input is visible`() {
        for (v in ClientVersion.entries) {
            val src = shader(v, "text.vsh", true)
            assertTrue("Usage hint" in src)
            assertTrue("Previous output" in src)
            assertTrue("Button labels" in src)
        }
    }

    @Test
    fun `visible input draws BlockyType text field sprites instead of transparent ones`() {
        val shown = PackBuilder.build(showInput = true)
        val hidden = PackBuilder.build(showInput = false)
        for (n in listOf("text_field", "text_field_highlighted")) {
            val sprite = "assets/minecraft/textures/gui/sprites/widget/$n.png"
            assertTrue(sprite in shown && sprite in hidden, n)
            assertFalse(shown.getValue(sprite).contentEquals(hidden.getValue(sprite)), "$n must not be transparent")
        }
    }

    @Test
    fun `visible input recolors the syntax highlighted box text to one calm color`() {
        for (v in ClientVersion.entries) {
            assertTrue("inputTextColor" in shader(v, "text.vsh", true), v.label)
            assertFalse("inputTextColor" in shader(v, "text.vsh", false), v.label)
        }
    }
}
