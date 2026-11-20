package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.format.TextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeatMapTest {
    @Test
    fun `blend mixes channel by channel`() {
        assertEquals(TextColor.color(0x808080), ColorBlend.blend(TextColor.color(0), TextColor.color(0xFFFFFF), 0.5))
        assertEquals(TextColor.color(0x102030), ColorBlend.blend(TextColor.color(0x102030), TextColor.color(0xFFFFFF), 0.0))
        assertEquals(TextColor.color(0xFFFFFF), ColorBlend.blend(TextColor.color(0x102030), TextColor.color(0xFFFFFF), 1.0))
    }

    @Test
    fun `heat roles run from error to main`() {
        val t = DefaultTheme
        assertEquals(t.errorColor, Role.HEAT0.color(t))
        assertEquals(ColorBlend.blend(t.errorColor, t.textColor, 0.5), Role.HEAT1.color(t))
        assertEquals(t.textColor, Role.HEAT2.color(t))
        assertEquals(ColorBlend.blend(t.mainColor, t.textColor, 0.5), Role.HEAT3.color(t))
        assertEquals(t.mainColor, Role.HEAT4.color(t))
    }

    @Test
    fun `a theme whose main equals its text uses sub for the slow half`() {
        val t = object : AbstractTheme {
            override val name = "same"
            override val mainColor: TextColor get() = textColor
        }
        assertEquals(t.subColor, Role.HEAT1.color(t))
        assertEquals(ColorBlend.blend(t.subColor, t.textColor, 0.5), Role.HEAT2.color(t))
        assertEquals(t.mainColor, Role.HEAT4.color(t))
    }

    private fun words(vararg w: String) = w.map { HeatWord(it, Role.TEXT, false) }

    @Test
    fun `words wrap at the line width without splitting`() {
        val lines = HeatMap.layout(words("aaaa", "bbbb", "cccc", "dddd"), width = 9, maxLines = 5, focus = null)
        assertEquals(listOf(listOf("aaaa", "bbbb"), listOf("cccc", "dddd")), lines.map { l -> l.map { it.word.text } })
        assertEquals(listOf(0, 5), lines[0].map { it.col })
        assertTrue(lines.all { l -> l.last().col + l.last().word.text.length <= 9 })
    }

    @Test
    fun `the window shows the first lines without a focus`() {
        val w = (0 until 40).map { "word" }
        val lines = HeatMap.layout(words(*w.toTypedArray()), width = 50, maxLines = 3, focus = null)
        assertEquals(3, lines.size)
        assertEquals(0, lines[0][0].index)
    }

    @Test
    fun `the window follows the focused word and keeps it on the second row`() {
        val w = (0 until 60).map { "word" } // 10 per line, 6 lines
        val lines = HeatMap.layout(words(*w.toTypedArray()), width = 50, maxLines = 3, focus = 35)
        assertEquals(3, lines.size)
        assertEquals(20, lines[0][0].index) // word 35 is on line 3, so the window starts at line 2 (word 20)
        assertTrue(lines[1].any { it.index == 35 })
    }

    @Test
    fun `the window stops at the end`() {
        val w = (0 until 60).map { "word" }
        val lines = HeatMap.layout(words(*w.toTypedArray()), width = 50, maxLines = 3, focus = 59)
        assertEquals(30, lines[0][0].index)
        assertEquals(59, lines.last().last().index)
    }

    @Test
    fun `short texts fill fewer lines`() {
        assertEquals(1, HeatMap.layout(words("a", "b"), 50, 3, 1).size)
        assertTrue(HeatMap.layout(emptyList(), 50, 3, null).isEmpty())
    }

    private fun text(runs: List<Run>) = runs.joinToString("") { it.text }

    @Test
    fun `the divider is a row of centered dots`() {
        val t = text(HeatMap.divider(null))
        assertEquals(49, t.length)
        assertTrue(t.startsWith("·") && t.endsWith("·"))
        assertTrue(!t.contains("··"))
        assertEquals(25, t.count { it == '·' })
    }

    @Test
    fun `the divider can carry the cursor info in the middle`() {
        val runs = HeatMap.divider("4s  wpm 39")
        val t = text(runs)
        assertEquals(49, t.length)
        assertTrue(" 4s  wpm 39 " in t)
        assertTrue(t.startsWith("·") && t.endsWith("·"))
        assertEquals(Role.MAIN, runs.first { "wpm" in it.text }.role)
        assertTrue(runs.filter { "wpm" !in it.text }.all { it.role == Role.SUB })
    }

    @Test
    fun `a too long info is cut so the row never exceeds the width`() {
        assertTrue(text(HeatMap.divider("x".repeat(80))).length <= 49)
    }
}
