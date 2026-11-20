package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MenuLayoutTest {
    private fun pill(text: String, action: String? = null, fill: Role? = Role.SUB_ALT) =
        Cell(text, Role.TEXT, action, fill = fill, pad = 1)

    private fun menu(
        rows: List<MenuRow?> = listOf(MenuRow(listOf(pill("ab", "a"), pill("cde", "b")), left = -150, gap = 4)),
        header: MenuRow = MenuRow(listOf(Cell("title", Role.SUB, big = true)), left = -150),
        footer: MenuRow? = null,
        focus: Focus? = null,
    ) = HudState.Menu(header, rows, footer, focus)

    // ---- geometry

    @Test
    fun `slots map to element ids and y offsets`() {
        assertEquals(UiFont.MENU_HEADER_ID, MenuLayout.elementId(MenuLayout.HEADER))
        assertEquals(UiFont.MENU_ROW_FIRST_ID, MenuLayout.elementId(1))
        assertEquals(UiFont.MENU_ROW_LAST_ID, MenuLayout.elementId(MenuLayout.BODY_ROWS))
        assertEquals(UiFont.MENU_FOOTER_ID, MenuLayout.elementId(MenuLayout.FOOTER))
        assertEquals(UiFont.MENU_ROW0_DY + UiFont.MENU_ROW_PITCH * 2, MenuLayout.slotDy(3))
        assertFailsWith<IllegalArgumentException> { MenuLayout.elementId(MenuLayout.FOOTER + 1) }
        assertFailsWith<IllegalArgumentException> { MenuLayout.slotDy(-1) }
    }

    @Test
    fun `cells are laid out left to right with the row gap, positioned cells stay put`() {
        val row = MenuRow(listOf(pill("ab"), pill("cde")), left = -150, gap = 4)
        assertEquals(listOf(-150, -150 + 4 * 6 + 4), row.cellLefts())
        assertEquals(listOf(24, 30), row.cellWidths())
        val placed = MenuRow(listOf(pill("ab"), Cell("x", Role.SUB, at = 100)), left = -150, gap = 4)
        assertEquals(listOf(-150, 100), placed.cellLefts())
    }

    @Test
    fun `big cells are wider per char`() {
        assertEquals(3 * UiFont.MONO_BIG_ADV, Cell("abc", Role.SUB, big = true).width)
        assertEquals((3 + 2) * UiFont.MONO_ADV, Cell("abc", Role.SUB, pad = 1).width)
    }

    // ---- rendering

    private fun texts(c: Component): List<TextComponent> =
        (if (c is TextComponent) listOf(c) else emptyList()) + c.children().flatMap { texts(it) }

    private fun markers(c: Component) = texts(c).mapNotNull { t -> t.color()?.let { UiFont.decode(it) }?.let { it to t } }

    private fun blockCount(title: Component, tall: Boolean): Int {
        val base = if (tall) UiFont.TALL_BASE else UiFont.BLOCK_BASE
        return texts(title).count { t -> t.content().any { it.code in base..base + UiFont.BLOCK_MAX_BIT } }
    }

    @Test
    fun `menu title is zero wide and uses menu element ids`() {
        val title = HudRenderer.render(menu(footer = MenuRow(listOf(Cell("q close", Role.SUB)), left = -150)), DefaultTheme)
        val ids = markers(title).mapNotNull { (m, _) -> (m as? Marker.Element)?.id }.toSet()
        assertTrue(Element.BACKGROUND in ids)
        assertTrue(ids.any { it in UiFont.MENU_HEADER_ID..UiFont.MENU_FOOTER_ID }, "menu ids missing: $ids")
        assertTrue(ids.all { it == 0 || it in UiFont.MENU_HEADER_ID..UiFont.MENU_FOOTER_ID }, "typing ids leaked: $ids")
    }

    @Test
    fun `cells with a fill draw a block behind their text`() {
        val title = HudRenderer.render(menu(), DefaultTheme)
        assertTrue(blockCount(title, tall = false) >= 2, "one fill block per pill")
        assertEquals(0, blockCount(title, tall = true), "no focus, no outline")
        assertTrue(texts(title).any { it.content() == "ab" } && texts(title).any { it.content() == "cde" })
    }

    @Test
    fun `the focused cell gets an outline block`() {
        val title = HudRenderer.render(menu(focus = Focus(slot = 1, cell = 1)), DefaultTheme)
        assertTrue(blockCount(title, tall = true) >= 1)
    }

    @Test
    fun `a row strip draws a block under the whole row`() {
        val row = MenuRow(listOf(Cell("tab", Role.SUB, "t", pad = 1)), left = -150, gap = 4, strip = Role.SUB_ALT)
        val with = HudRenderer.render(menu(rows = listOf(row)), DefaultTheme)
        val without = HudRenderer.render(menu(rows = listOf(row.copy(strip = null))), DefaultTheme)
        assertTrue(blockCount(with, tall = false) > blockCount(without, tall = false))
    }

    @Test
    fun `big header text uses the big font`() {
        val title = HudRenderer.render(menu(), DefaultTheme)
        assertTrue(texts(title).any { it.content() == "title" && it.font() == UiFont.MONO_BIG })
    }

    @Test
    fun `block colors come from the cell roles`() {
        val title = HudRenderer.render(menu(rows = listOf(MenuRow(listOf(pill("ab", "a", Role.MAIN)), left = -150))), DefaultTheme)
        val main = texts(title).filter { t -> t.content().any { it.code in UiFont.BLOCK_BASE..UiFont.BLOCK_BASE + UiFont.BLOCK_MAX_BIT } }
            .mapNotNull { it.color() }
        assertTrue(main.any { c -> kotlin.math.abs(c.red() - DefaultTheme.mainColor.red()) <= 12 && kotlin.math.abs(c.green() - DefaultTheme.mainColor.green()) <= 7 })
    }

    @Test
    fun `no cursor glyph is drawn`() {
        val title = HudRenderer.render(menu(focus = Focus(1, 0)), DefaultTheme)
        assertTrue(texts(title).none { t -> t.content().any { it.code == 0xE102 } })
    }

    @Test
    fun `overlapping cells are rejected`() {
        val row = MenuRow(listOf(Cell("aaaa", Role.SUB, at = 0), Cell("bbbb", Role.SUB, at = 6)), left = -150)
        assertFailsWith<IllegalArgumentException> { HudRenderer.render(menu(rows = listOf(row)), DefaultTheme) }
    }
}

class SidePanelTest {
    private fun texts(c: net.kyori.adventure.text.Component): List<net.kyori.adventure.text.TextComponent> =
        (if (c is net.kyori.adventure.text.TextComponent) listOf(c) else emptyList()) + c.children().flatMap { texts(it) }

    private fun state(side: List<List<Run>>) = TypingStateForTest.state().copy(side = side)

    @Test
    fun `side lines use the side element ids and the title stays zero wide`() {
        val title = HudRenderer.render(
            state(listOf(listOf(Run("Q", Role.MAIN), Run(" settings", Role.SUB)), listOf(Run("Ctrl+Q", Role.MAIN), Run(" admin", Role.SUB)))),
            DefaultTheme,
        )
        val ids = texts(title).mapNotNull { t -> t.color()?.let { UiFont.decode(it) } as? Marker.Element }.map { it.id }.toSet()
        assertTrue(UiFont.SIDE_FIRST_ID in ids && UiFont.SIDE_FIRST_ID + 1 in ids, "side ids missing: $ids")
        assertTrue(texts(title).any { it.content() == "Ctrl+Q" })
    }

    @Test
    fun `no side lines draw no side ids`() {
        val title = HudRenderer.render(state(emptyList()), DefaultTheme)
        val ids = texts(title).mapNotNull { t -> t.color()?.let { UiFont.decode(it) } as? Marker.Element }.map { it.id }
        assertTrue(ids.none { it >= UiFont.SIDE_FIRST_ID && it < UiFont.SIDE_FIRST_ID + UiFont.SIDE_ROWS })
    }

    @Test
    fun `side ids sit between the menu rows and the menu footer`() {
        assertTrue(UiFont.SIDE_FIRST_ID > UiFont.MENU_ROW_LAST_ID)
        assertTrue(UiFont.SIDE_FIRST_ID + UiFont.SIDE_ROWS - 1 < UiFont.MENU_FOOTER_ID)
    }

    @Test
    fun `shader anchors side rows in the top right corner`() {
        for (v in com.javaniac.blockytype.pack.ClientVersion.entries) {
            val src = com.javaniac.blockytype.pack.Shaders.files(v)
                .entries.single { it.key.endsWith("core/text.vsh") }.value
            assertTrue("gw - ${UiFont.SIDE_MARGIN}.0" in src, v.label)
            assertTrue("${UiFont.SIDE_TOP}.0 + ${UiFont.SIDE_ROW_PITCH}.0 * float(id - ${UiFont.SIDE_FIRST_ID})" in src, v.label)
        }
    }
}

private object TypingStateForTest {
    fun state() = HudState.Typing(
        config = listOf(Run("words", Role.MAIN)),
        live = listOf(Run("0", Role.MAIN)),
        lines = listOf(HudLine.untyped("the quick")),
    )
}
