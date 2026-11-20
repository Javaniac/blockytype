package com.javaniac.blockytype.ui

import com.javaniac.blockytype.db.PlayerStats
import com.javaniac.blockytype.game.PaceCaret
import com.javaniac.blockytype.game.TargetText
import com.javaniac.blockytype.game.TypingTest
import com.javaniac.blockytype.game.TypingView
import com.javaniac.blockytype.game.ViewOptions
import com.javaniac.blockytype.pack.PackBuilder
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaretStyleTest {
    private val ms = 1_000_000L

    private fun typing(style: CaretStyle, pace: PaceMark? = null) = HudState.Typing(
        config = emptyList(), live = emptyList(),
        lines = listOf(HudLine.untyped("hello world")),
        caretLine = 0, caretColumn = 3, caretStyle = style, pace = pace,
    )

    private fun text(state: HudState) = PlainTextComponentSerializer.plainText().serialize(HudRenderer.render(state, DefaultTheme))

    @Test
    fun `styles are read from setting values`() {
        assertEquals(CaretStyle.OFF, CaretStyle.of("off"))
        assertEquals(CaretStyle.LINE, CaretStyle.of("default"))
        assertEquals(CaretStyle.BLOCK, CaretStyle.of("block"))
        assertEquals(CaretStyle.OUTLINE, CaretStyle.of("outline"))
        assertEquals(CaretStyle.UNDERLINE, CaretStyle.of("underline"))
        assertEquals(CaretStyle.LINE, CaretStyle.of("bogus"))
    }

    @Test
    fun `every style renders a zero width title with its own glyph`() {
        assertTrue(UiFont.CARET.toString() in text(typing(CaretStyle.LINE)))
        assertTrue(UiFont.CARET_OUTLINE.toString() in text(typing(CaretStyle.OUTLINE)))
        assertTrue(UiFont.CARET_UNDERLINE.toString() in text(typing(CaretStyle.UNDERLINE)))
        for (g in listOf(UiFont.CARET, UiFont.CARET_OUTLINE, UiFont.CARET_UNDERLINE)) {
            assertTrue(g.toString() !in text(typing(CaretStyle.OFF)), "off draws no caret glyph")
            if (g != UiFont.CARET) assertTrue(g.toString() !in text(typing(CaretStyle.LINE)))
        }
        // The block is a filled cell behind the letter under the caret, not a glyph of its own.
        val block = text(typing(CaretStyle.BLOCK))
        assertTrue(UiFont.CARET.toString() !in block)
        assertTrue((UiFont.BLOCK_BASE..UiFont.BLOCK_BASE + UiFont.BLOCK_MAX_BIT).any { it.toChar() in block })
    }

    @Test
    fun `the pace caret is drawn in the chosen style`() {
        val pace = PaceMark(line = 0, column = 7, style = CaretStyle.OUTLINE)
        assertTrue(UiFont.CARET_OUTLINE.toString() in text(typing(CaretStyle.LINE, pace)))
        assertTrue(UiFont.CARET_OUTLINE.toString() !in text(typing(CaretStyle.LINE)))
        val off = PaceMark(line = 0, column = 7, style = CaretStyle.OFF)
        assertTrue(text(typing(CaretStyle.LINE, off)) == text(typing(CaretStyle.LINE)))
    }

    @Test
    fun `the pack has the new caret glyphs`() {
        val files = PackBuilder.build()
        assertNotNull(files["assets/blockytype/textures/font/caret_outline.png"])
        assertNotNull(files["assets/blockytype/textures/font/caret_underline.png"])
        val ui = String(files["assets/blockytype/font/ui.json"]!!)
        assertTrue("caret_outline.png" in ui && "caret_underline.png" in ui)
        assertTrue(UiFont.CARET_OUTLINE.code != UiFont.CARET.code && UiFont.CARET_UNDERLINE.code != UiFont.CARET_OUTLINE.code)
    }

    // ---- pace caret position

    private fun view(o: ViewOptions, pace: Double?, typed: String = "", elapsedMs: Long = 2000): HudState.Typing {
        val t = TypingTest(TargetText(listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta")))
        var now = 0L
        for (c in typed) {
            t.apply(t.typed + c, now * ms)
            now += 10
        }
        return TypingView.build(t, elapsedMs * ms, "english", o, paceWpm = pace)
    }

    @Test
    fun `the pace caret moves at the target speed`() {
        val o = ViewOptions(paceStyle = CaretStyle.LINE)
        // 60 wpm = 5 chars per second: after 2 s the pace caret is before char 10.
        val s = view(o, 60.0, typed = "a")
        assertEquals(PaceMark(0, 10, CaretStyle.LINE), s.pace)
        assertEquals(PaceMark(0, 5, CaretStyle.LINE), view(o, 60.0, typed = "a", elapsedMs = 1000).pace)
    }

    @Test
    fun `no pace caret without a target, before the start or when off`() {
        assertNull(view(ViewOptions(), null, typed = "a").pace)
        assertNull(view(ViewOptions(paceStyle = CaretStyle.LINE), 60.0, typed = "").pace) // not started
        assertNull(view(ViewOptions(paceStyle = CaretStyle.OFF), 60.0, typed = "a").pace)
    }

    @Test
    fun `the pace caret stays on the last char at the end of the text`() {
        val s = view(ViewOptions(paceStyle = CaretStyle.LINE), 600.0, typed = "a", elapsedMs = 20_000)
        assertNotNull(s.pace)
    }

    @Test
    fun `the pace caret is left out when its line is not visible`() {
        val words = List(60) { "word$it" }
        val t = TypingTest(TargetText(words))
        t.apply("w", 0)
        // far ahead: 600 wpm for 10 s = 1000 chars, past the three visible lines
        val s = TypingView.build(t, 10_000 * ms, "en", ViewOptions(paceStyle = CaretStyle.LINE), paceWpm = 600.0)
        assertNull(s.pace)
    }

    @Test
    fun `tape mode places the pace caret relative to the window`() {
        val o = ViewOptions(tape = ViewOptions.Tape.LETTER, tapeMargin = 50, width = 30, paceStyle = CaretStyle.LINE)
        val s = view(o, 60.0, typed = "alpha b") // caret index 7, pace index 10
        assertEquals(15 + (10 - 7), s.pace!!.column)
        assertEquals(0, s.pace!!.line)
    }

    @Test
    fun `pace targets come from the player's results`() {
        val stats = PlayerStats(best = 120.0, last = 80.0, average = 95.0, accuracy = 97.0)
        assertNull(PaceCaret.target("off", 60, stats))
        assertEquals(95.0, PaceCaret.target("average", 60, stats))
        assertEquals(120.0, PaceCaret.target("pb", 60, stats))
        assertEquals(80.0, PaceCaret.target("last", 60, stats))
        assertEquals(60.0, PaceCaret.target("custom", 60, null)) // needs no results
        assertNull(PaceCaret.target("average", 60, null))
        assertNull(PaceCaret.target("pb", 60, PlayerStats(null, null, null, null)))
    }

    @Test
    fun `the view options read the caret settings`() {
        val map = mapOf("caret-style" to "block", "pace-caret-style" to "underline", "pace-caret" to "pb", "pace-caret-custom" to "100")
        val o = ViewOptions.from { map[it] ?: "off" }
        assertEquals(CaretStyle.BLOCK, o.caret)
        assertEquals(CaretStyle.UNDERLINE, o.paceStyle)
        assertEquals("pb", o.paceMode)
        assertEquals(100, o.paceCustom)
        assertEquals(CaretStyle.LINE, ViewOptions().caret)
    }
}
