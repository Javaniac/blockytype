package com.javaniac.blockytype.ui

import com.javaniac.blockytype.pack.PackBuilder
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FaceArtTest {
    private fun menu(face: FaceArt?) = HudState.Menu(
        header = MenuRow(listOf(Cell("title", Role.SUB)), -150),
        rows = listOf(MenuRow(listOf(Cell("name", Role.TEXT, at = -100)), -150, face = face)),
        footer = null,
    )

    private fun flatten(c: Component): List<Component> = listOf(c) + c.children().flatMap { flatten(it) }

    private fun isFaceGlyph(ch: Char) = ch.code in UiFont.FACE_BASE until UiFont.FACE_BASE + 8

    @Test
    fun `a face needs 64 pixels`() {
        assertFailsWith<IllegalArgumentException> { FaceArt(-150, IntArray(63)) }
        assertEquals(24, UiFont.FACE_SIZE)
        assertEquals(8 * UiFont.FACE_PIXEL, UiFont.FACE_SIZE)
    }

    @Test
    fun `a menu with a face renders with a zero width title`() {
        val plain = PlainTextComponentSerializer.plainText()
        val without = plain.serialize(HudRenderer.render(menu(null), DefaultTheme))
        val with = plain.serialize(HudRenderer.render(menu(FaceArt(-150, IntArray(64) { 0x336699 })), DefaultTheme)) // build() checks width 0
        assertTrue(without.none(::isFaceGlyph))
        for (row in 0 until 8) assertTrue(with.any { it.code == UiFont.FACE_BASE + row }, "row $row")
    }

    @Test
    fun `equal neighbours share one component and every pixel keeps its own color`() {
        val flat = flatten(HudRenderer.render(menu(FaceArt(-150, IntArray(64) { 0x336699 })), DefaultTheme))
            .filterIsInstance<net.kyori.adventure.text.TextComponent>().filter { c -> c.content().any(::isFaceGlyph) }
        assertEquals(8, flat.size, "one component per row of a one color face")

        val checker = IntArray(64) { if ((it % 8 + it / 8) % 2 == 0) 0x112233 else 0xAABBCC }
        val parts = flatten(HudRenderer.render(menu(FaceArt(-150, checker)), DefaultTheme))
            .filterIsInstance<net.kyori.adventure.text.TextComponent>().filter { c -> c.content().any(::isFaceGlyph) }
        assertEquals(64, parts.size)
        // The pixel colors are snapped to the HUD marker (a few units off at most), never far from the skin color.
        val first = parts.first().color()!!
        assertTrue(kotlin.math.abs(first.red() - 0x11) <= 12 && kotlin.math.abs(first.green() - 0x22) <= 4 && kotlin.math.abs(first.blue() - 0x33) <= 4)
        assertEquals(3, first.red() and 15, "R & 15 == 3 marks a HUD vertex")
    }

    @Test
    fun `the face is drawn in the element of its row`() {
        val parts = flatten(HudRenderer.render(menu(FaceArt(-150, IntArray(64) { 0x336699 })), DefaultTheme))
            .filterIsInstance<net.kyori.adventure.text.TextComponent>().filter { c -> c.content().any(::isFaceGlyph) }
        val ids = parts.map { UiFont.decode(it.color()!!) }.toSet()
        assertEquals(setOf<Marker?>(Marker.Element(UiFont.MENU_ROW_FIRST_ID)), ids)
    }

    @Test
    fun `the pack has the face glyphs`() {
        val files = PackBuilder.build()
        assertNotNull(files["assets/blockytype/textures/font/face.png"])
        assertTrue("face.png" in String(files["assets/blockytype/font/ui.json"]!!))
    }
}
