package com.javaniac.blockytype.themes

import com.javaniac.blockytype.toColor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RandomThemeTest {
    private fun theme(n: String, bg: Int) = object : AbstractTheme {
        override val name = n
        override val bgColor = bg.toColor()
    }

    private val themes = listOf(theme("Night", 0x101010), theme("Day", 0xF0F0F0), theme("Dusk", 0x303040), theme("Paper", 0xEEEEDD))

    @Test
    fun `off picks nothing`() {
        assertNull(RandomTheme.pick("off", themes))
        assertNull(RandomTheme.pick("bogus", themes))
    }

    @Test
    fun `on picks any theme, lowercase`() {
        val seen = (0 until 200).mapNotNull { RandomTheme.pick("on", themes, Random(it)) }.toSet()
        assertEquals(setOf("night", "day", "dusk", "paper"), seen)
    }

    @Test
    fun `light and dark pick by background brightness`() {
        for (i in 0 until 50) {
            assertTrue(RandomTheme.pick("light", themes, Random(i)) in setOf("day", "paper"))
            assertTrue(RandomTheme.pick("dark", themes, Random(i)) in setOf("night", "dusk"))
        }
    }

    @Test
    fun `no fitting theme picks nothing`() {
        assertNull(RandomTheme.pick("light", listOf(themes[0])))
        assertNull(RandomTheme.pick("on", emptyList()))
    }

    @Test
    fun `the monkeytype themes have both kinds`() {
        assertTrue(MONKEYTYPE_THEMES.any(RandomTheme::isLight))
        assertTrue(MONKEYTYPE_THEMES.any { !RandomTheme.isLight(it) })
    }
}
