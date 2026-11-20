package com.javaniac.blockytype.pack

import com.javaniac.blockytype.themes.MonkeytypeTheme
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BoxColorsTest {
    private val base = PackVariant("jetbrains-mono", antiAlias = true, showInput = true)
    private val colors = BoxColors(fill = 0x102030, border = 0x405060, borderFocus = 0x708090, text = 0x123456)

    private fun textVsh(v: ClientVersion, variant: PackVariant) =
        Shaders.files(v, variant.showInput, variant.antiAlias, variant.box).entries.single { it.key.endsWith("core/text.vsh") }.value

    @Test
    fun `box colors change the pack`() {
        val a = PackBuilder.zip(PackBuilder.build(base))
        val b = PackBuilder.zip(PackBuilder.build(base.copy(box = colors)))
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `the same colors build the same pack`() {
        assertContentEquals(
            PackBuilder.zip(PackBuilder.build(base.copy(box = colors))),
            PackBuilder.zip(PackBuilder.build(base.copy(box = colors))),
        )
    }

    @Test
    fun `text color reaches the shader on every client version`() {
        for (v in ClientVersion.entries) {
            val src = textVsh(v, base.copy(box = colors))
            assertTrue("vec3(18.0 / 255.0, 52.0 / 255.0, 86.0 / 255.0)" in src, "${v.label}")
        }
    }

    @Test
    fun `hidden input ignores the box colors`() {
        val of = PackVariant.of("jetbrains-mono", "on", "hide", colors)
        assertEquals(BoxColors.DEFAULT, of.box)
        assertEquals(colors, PackVariant.of("jetbrains-mono", "on", "show", colors).box)
    }

    @Test
    fun `colors come from the theme roles`() {
        val t = MonkeytypeTheme("x", bg = 0x000001, main = 0xAA0000, caret = 0, sub = 0x00BB00, subAlt = 0x0000CC, text = 0xDDDDDD, error = 0)
        val c = BoxColors.of(t)
        assertEquals(0x0000CC, c.fill)
        assertEquals(0x00BB00, c.border)
        assertEquals(0xAA0000, c.borderFocus)
        assertEquals(0xDDDDDD, c.text)
    }

    @Test
    fun `text color never carries the HUD marker`() {
        val t = MonkeytypeTheme("x", 0, 0, 0, 0, 0, text = 0x030303, error = 0)
        assertNotEquals(3, (BoxColors.of(t).text shr 16) and 7)
    }
}
