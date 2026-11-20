package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.Run
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.KeybindComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KeybindsTest {
    private val sep = Keybinds.SEP

    @Test
    fun `defaults are the vanilla keys`() {
        assertEquals(Keybinds("Q", "F", "right click", "A", "D"), Keybinds.DEFAULT)
        assertEquals("Ctrl+Q", Keybinds.DEFAULT.admin)
    }

    @Test
    fun `parses the resolved names in probe order`() {
        assertEquals(Keybinds("G", "H", "Right Button", "J", "K"), Keybinds.parse(listOf("G", "H", "Right Button", "J", "K").joinToString(sep)))
        assertEquals("Ctrl+G", Keybinds.parse(listOf("G", "H", "X", "A", "D").joinToString(sep))!!.admin)
    }

    @Test
    fun `rejects the wrong number of parts`() {
        assertNull(Keybinds.parse(""))
        assertNull(Keybinds.parse("G${sep}H"))
        assertNull(Keybinds.parse("G${sep}H${sep}X"))
        assertNull(Keybinds.parse(listOf("G", "H", "X", "A", "D", "Y").joinToString(sep)))
    }

    @Test
    fun `rejects unresolved keybind ids`() {
        assertNull(Keybinds.parse(listOf("key.drop", "F", "Right Button", "A", "D").joinToString(sep)))
    }

    @Test
    fun `rejects blank, long, control and private use text`() {
        assertNull(Keybinds.parse("${sep}F${sep}X"))
        assertNull(Keybinds.parse("   ${sep}F${sep}X"))
        assertNull(Keybinds.parse("${"x".repeat(25)}${sep}F${sep}X"))
        assertNull(Keybinds.parse("a\u0007b${sep}F${sep}X"))
        assertNull(Keybinds.parse("${sep}F${sep}X"))
    }

    @Test
    fun `a name that contains the separator cannot smuggle a part`() {
        assertNull(Keybinds.parse(listOf("A", "B", "C", "D", "E", "F").joinToString(sep)))
    }

    @Test
    fun `the probe name holds one keybind component per key in order`() {
        val keys = flatten(KeybindProbe.name()).filterIsInstance<KeybindComponent>().map { it.keybind() }
        assertEquals(listOf("key.drop", "key.swapOffhand", "key.use", "key.left", "key.right"), keys)
    }

    @Test
    fun `probe name fits the 50 char rename box with long key names`() {
        val raw = listOf("x".repeat(8), "x".repeat(8), "Right Button", "x".repeat(8), "x".repeat(8)).joinToString(sep)
        assertNotNull(Keybinds.parse(raw))
        assert(raw.length <= 50) { raw.length }
    }

    @Test
    fun `side lines show the menu key, plus the admin chord for admins`() {
        val k = Keybinds("G", "H", "X", "A", "D")
        assertEquals(listOf(listOf(Run("G", Role.MAIN), Run(" settings", Role.SUB))), k.side(isAdmin = false))
        assertEquals(
            listOf(
                listOf(Run("G", Role.MAIN), Run(" settings", Role.SUB)),
                listOf(Run("Ctrl+G", Role.MAIN), Run(" admin", Role.SUB)),
            ),
            k.side(isAdmin = true),
        )
    }

    @Test
    fun `hints are joined by a centered dot`() {
        val joined = Keybinds.join(listOf(listOf(Run("F", Role.MAIN), Run(" restart", Role.SUB)), listOf(Run("Q", Role.MAIN), Run(" settings", Role.SUB))))
        assertEquals(
            listOf(Run("F", Role.MAIN), Run(" restart", Role.SUB), Run(" · ", Role.SUB), Run("Q", Role.MAIN), Run(" settings", Role.SUB)),
            joined,
        )
        assertEquals(emptyList(), Keybinds.join(emptyList()))
    }

    @Test
    fun `the type hint names the real use key`() {
        assertEquals(listOf(Run("Right-Mouse", Role.MAIN), Run(" to type", Role.SUB)), Keybinds(use = "Right-Mouse").typeHint())
        assertEquals(listOf(Run("Right-Mouse", Role.MAIN), Run(" to type", Role.SUB)), Keybinds(use = "Right Button").typeHint())
        assertEquals(listOf(Run("Left-Mouse", Role.MAIN), Run(" to type", Role.SUB)), Keybinds(use = "Left Button").typeHint())
        assertEquals(listOf(Run("E", Role.MAIN), Run(" to type", Role.SUB)), Keybinds(use = "E").typeHint())
    }

    private fun flatten(c: Component): List<Component> = listOf(c) + c.children().flatMap { flatten(it) }
}
