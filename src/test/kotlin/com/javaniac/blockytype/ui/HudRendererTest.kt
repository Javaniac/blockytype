package com.javaniac.blockytype.ui

import com.javaniac.blockytype.game.TargetText
import com.javaniac.blockytype.game.TestStats
import com.javaniac.blockytype.game.ResultView
import com.javaniac.blockytype.game.TypingTest
import com.javaniac.blockytype.themes.DefaultTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudRendererTest {
    @Test
    fun `space strings have the requested advance`() {
        for (n in -2047..2047) assertEquals(n, UiFont.spaceAdvance(UiFont.space(n)), "n=$n")
    }

    @Test
    fun `marked colors carry the element id`() {
        for (id in 0..15) {
            val c = UiFont.mark(DefaultTheme.mainColor, id)
            assertEquals(3, c.red() and 7)
            assertEquals(id, ((c.green() and 3) shl 2) or (c.blue() and 3))
        }
    }

    @Test
    fun `graph strings return to the origin`() {
        val g = Graph(listOf(10.0, 55.0, 90.0, 40.0), listOf(20.0, 60.0, 100.0, 50.0), listOf(0, 1, 0, 2))
        val max = ResultGraph.yMax(g)
        assertEquals(100, max)
        assertEquals(0, ResultGraph.advanceOf(ResultGraph.series(g.wpm, max)))
        assertEquals(0, ResultGraph.advanceOf(ResultGraph.hline(0)))
        assertTrue(ResultGraph.errorColumns(g).size == 2)
    }

    @Test
    fun `typing and result screens render with a zero width title`() {
        val test = TypingTest(TargetText(listOf("the", "quick", "fox")))
        var t = 0L
        for (c in "the quick fox") {
            test.apply(test.typed + c, t * 1_000_000)
            t += 400
        }
        HudRenderer.render(TypingTest(TargetText.sample()).toHudState(0), DefaultTheme)
        val result = ResultView.toHudState(TestStats.result(test), test)
        HudRenderer.render(result, DefaultTheme)
    }

    @Test
    fun `focus outline uses the accent color unless the pill is already filled with it`() {
        assertEquals(Role.MAIN, HudRenderer.outlineRole(null))
        assertEquals(Role.MAIN, HudRenderer.outlineRole(Role.SUB_ALT))
        assertEquals(Role.TEXT, HudRenderer.outlineRole(Role.MAIN))
    }

    @Test
    fun `graph border covers all four edges and returns to the origin`() {
        val b = ResultGraph.border()
        assertTrue(ResultGraph.advanceOf(ResultGraph.border(1)) == 0)
        val dots = b.filter { it.code in ResultGraph.DOT_BASE until ResultGraph.DOT_BASE + ResultGraph.H }
        assertEquals(2 * ((ResultGraph.W + 1) / 2) + 2 * ((ResultGraph.H - 2 + 1) / 2), dots.length)
        assertEquals(0, ResultGraph.advanceOf(b))
        assertTrue((ResultGraph.DOT_BASE + ResultGraph.H - 1).toChar() in b)
    }

    @Test
    fun `the data keeps a margin to the frame`() {
        val m = ResultGraph.MARGIN
        assertEquals(m, ResultGraph.xOf(0, 10))
        assertEquals(ResultGraph.W - 1 - m, ResultGraph.xOf(9, 10))
        val heights = ResultGraph.series(listOf(0.0, 100.0, 50.0), 100)
            .filter { it.code in ResultGraph.DOT_BASE until ResultGraph.DOT_BASE + ResultGraph.H }
            .map { it.code - ResultGraph.DOT_BASE }
        assertEquals(m, heights.min())
        assertEquals(ResultGraph.H - 1 - m, heights.max())
    }

    @Test
    fun `the result with a cursor renders with a zero width title`() {
        val test = TypingTest(TargetText(listOf("the", "quick", "fox")))
        var t = 0L
        for (c in "the quick fox") {
            test.apply(test.typed + c, t * 1_000_000)
            t += 400
        }
        val r = TestStats.result(test)
        for (cursor in listOf(null, 0, 1, r.perSecond.lastIndex)) {
            HudRenderer.render(ResultView.toHudState(r, test, cursor = cursor), DefaultTheme)
        }
    }

    @Test
    fun `the heatmap legend shows the five levels from slow to fast between its labels`() {
        val items = HudRenderer.legend()
        assertEquals(listOf("slow", " ", " ", " ", " ", " ", "fast"), items.map { it.text })
        assertEquals(Role.entries.filter { it.name.startsWith("HEAT") }, items.mapNotNull { it.fill })
        assertEquals(items.sortedBy { it.x }, items)
        items.zipWithNext().forEach { (a, b) -> assertTrue(a.x + a.width <= b.x) }
    }
}
