package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaretMotionTest {
    @Test
    fun `caret moving one column on the same line slides in from that side`() {
        assertEquals(CaretMove.FROM_LEFT, CaretMove.between(0 to 5, 0 to 6))
        assertEquals(CaretMove.FROM_RIGHT, CaretMove.between(0 to 6, 0 to 5))
    }

    @Test
    fun `other caret changes do not animate`() {
        assertEquals(CaretMove.NONE, CaretMove.between(0 to 5, 0 to 5))
        assertEquals(CaretMove.NONE, CaretMove.between(0 to 5, 0 to 7))
        assertEquals(CaretMove.NONE, CaretMove.between(0 to 49, 1 to 0)) // line wrap
        assertEquals(CaretMove.NONE, CaretMove.between(null, 0 to 0)) // first render
        assertEquals(CaretMove.NONE, CaretMove.between(0 to 5, -1 to 0)) // hidden caret
    }

    @Test
    fun `plain element colors never look like carets`() {
        for (r in 0..255) for (id in 0..15) {
            val c = UiFont.mark(TextColor.color(r, 128, 128), id)
            assertEquals(3, c.red() and 7)
            assertEquals(0, c.red() and 8, "r=$r id=$id")
        }
    }

    @Test
    fun `caret colors carry element id, caret flag and direction`() {
        for (id in 3..5) for (move in CaretMove.entries) {
            val c = UiFont.markCaret(DefaultTheme.caretColor, id, move)
            assertEquals(3, c.red() and 7, "still a HUD marker")
            assertEquals(id, ((c.green() and 3) shl 2) or (c.blue() and 3), "element id")
            assertEquals(8, c.red() and 8, "caret flag")
            assertEquals(move != CaretMove.NONE, (c.green() and 4) != 0, "animate flag")
            if (move != CaretMove.NONE) assertEquals(move == CaretMove.FROM_LEFT, (c.blue() and 4) != 0, "direction")
        }
    }

    @Test
    fun `caret color stays close to the theme color`() {
        val c = UiFont.markCaret(DefaultTheme.caretColor, 3, CaretMove.FROM_LEFT)
        val t = DefaultTheme.caretColor
        assertTrue(kotlin.math.abs(c.red() - t.red()) <= 16)
        assertTrue(kotlin.math.abs(c.green() - t.green()) <= 8)
        assertTrue(kotlin.math.abs(c.blue() - t.blue()) <= 8)
    }

    private fun allText(c: Component): List<TextComponent> =
        (if (c is TextComponent) listOf(c) else emptyList()) + c.children().flatMap { allText(it) }

    private fun caretColors(state: HudState.Typing): List<TextColor> =
        allText(HudRenderer.render(state, DefaultTheme))
            .filter { it.content() == UiFont.CARET.toString() }
            .map { it.color()!! }

    private fun state(move: CaretMove) = HudState.Typing(
        config = emptyList(), live = emptyList(),
        lines = listOf(HudLine.untyped("hello world")),
        caretLine = 0, caretColumn = 3, caretMove = move,
    )

    @Test
    fun `renderer flags the caret glyph and only it`() {
        val colors = caretColors(state(CaretMove.FROM_LEFT))
        assertEquals(1, colors.size)
        assertTrue(colors.single().red() and 8 != 0)
        val others = allText(HudRenderer.render(state(CaretMove.FROM_LEFT), DefaultTheme))
            .filter { it.content() != UiFont.CARET.toString() && it.color() != null }
        assertTrue(others.all { it.color()!!.red() and 8 == 0 }, "no other glyph may carry the caret flag")
    }

    @Test
    fun `renderer without a move still flags the caret but does not animate`() {
        val c = caretColors(state(CaretMove.NONE)).single()
        assertTrue(c.red() and 8 != 0)
        assertFalse(c.green() and 4 != 0)
    }

    @Test
    fun `caret title is still zero width`() {
        for (move in CaretMove.entries) HudRenderer.render(state(move), DefaultTheme)
    }
}
