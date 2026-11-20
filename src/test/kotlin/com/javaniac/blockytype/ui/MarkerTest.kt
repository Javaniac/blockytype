package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.format.TextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkerTest {
    private val bases = listOf(
        TextColor.color(0, 0, 0), TextColor.color(255, 255, 255), TextColor.color(170, 170, 170),
        TextColor.color(224, 224, 224), TextColor.color(85, 255, 85), TextColor.color(7, 200, 100),
        DefaultTheme.mainColor, DefaultTheme.subColor, DefaultTheme.errorColor, DefaultTheme.caretColor,
    )

    @Test
    fun `plain marks round trip ids 0 to 31`() {
        for (base in bases) for (id in 0..31) {
            val c = UiFont.mark(base, id)
            assertEquals(Marker.Element(id), UiFont.decode(c), "id $id base $base")
        }
    }

    @Test
    fun `plain marks stay close to the theme color`() {
        for (base in bases) for (id in 0..31) {
            val c = UiFont.mark(base, id)
            assertTrue(kotlin.math.abs(c.red() - base.red()) <= 12, "red")
            assertTrue(kotlin.math.abs(c.green() - base.green()) <= 7, "green")
            assertTrue(kotlin.math.abs(c.blue() - base.blue()) <= 3, "blue")
        }
    }

    @Test
    fun `ids outside 0 to 31 are rejected`() {
        assertFailsWith<IllegalArgumentException> { UiFont.mark(DefaultTheme.mainColor, 32) }
        assertFailsWith<IllegalArgumentException> { UiFont.mark(DefaultTheme.mainColor, -1) }
    }

    @Test
    fun `carets still decode as carets`() {
        for (id in 0..15) for (move in CaretMove.entries) {
            assertEquals(Marker.Caret(id), UiFont.decode(UiFont.markCaret(DefaultTheme.caretColor, id, move)))
        }
    }

    @Test
    fun `no vanilla text color looks like a marker`() {
        val vanilla = listOf(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555,
            0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF, 0xE0E0E0, 0xA0A0A0, 0x808080)
        for (rgb in vanilla) assertEquals(null, UiFont.decode(TextColor.color(rgb)), "0x%06X".format(rgb))
    }
}
