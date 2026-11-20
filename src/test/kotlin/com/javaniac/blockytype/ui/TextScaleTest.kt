package com.javaniac.blockytype.ui

import com.javaniac.blockytype.game.TestFactory
import com.javaniac.blockytype.game.ViewOptions
import com.javaniac.blockytype.pack.PackBuilder
import com.javaniac.blockytype.settings.SettingsCatalog
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TextScaleTest {
    private fun catalog(id: String) = SettingsCatalog.byId(id)!!.default

    @Test
    fun `scales are read from the setting`() {
        assertEquals(TextScale.X1, TextScale.of("1"))
        assertEquals(TextScale.X1_5, TextScale.of("1.5"))
        assertEquals(TextScale.X2, TextScale.of("2"))
        assertEquals(TextScale.X1, TextScale.of("bogus"))
        assertEquals(listOf("1", "1.5", "2"), SettingsCatalog.byId("font-size")!!.options.values())
    }

    @Test
    fun `advances are whole units and a line fits the smallest screen`() {
        assertEquals(listOf(6, 9, 12), TextScale.entries.map { it.adv })
        assertEquals(listOf(50, 33, 25), TextScale.entries.map { it.maxChars })
        for (s in TextScale.entries) assertTrue(s.maxChars * s.adv <= 300)
    }

    @Test
    fun `the line width is capped by the scale`() {
        fun view(width: String, size: String) = ViewOptions.from { if (it == "max-line-width") width else if (it == "font-size") size else catalog(it) }
        assertEquals(50, view("50", "1").width)
        assertEquals(33, view("50", "1.5").width)
        assertEquals(25, view("50", "2").width)
        assertEquals(30, view("30", "1.5").width)
        assertEquals(25, view("40", "2").width)
        assertEquals(TextScale.X2, view("50", "2").scale)
    }

    @Test
    fun `the test text wraps at the same width as the view`() {
        for (size in listOf("1", "1.5", "2")) {
            val test = TestFactory.create({ if (it == "font-size") size else catalog(it) }).test
            val line = test.target.lines.first().text
            assertTrue(line.length <= TextScale.of(size).maxChars, "$size: ${line.length}")
        }
    }

    @Test
    fun `the renderer uses the font of the scale and keeps the title zero width`() {
        for (scale in TextScale.entries) {
            val state = HudState.Typing(
                config = emptyList(), live = emptyList(),
                lines = listOf(HudLine.untyped("hello world")), caretLine = 0, caretColumn = 3, scale = scale,
                caretStyle = CaretStyle.BLOCK,
            )
            val component = HudRenderer.render(state, DefaultTheme) // build() checks the zero width
            assertNotNull(component)
            val used = component.children().mapNotNull { it.font() }.toSet()
            assertTrue(scale.font in used, "${scale.font} not in $used")
            val text = PlainTextComponentSerializer.plainText().serialize(component)
            assertTrue("h" in text && "w" in text)
        }
    }

    @Test
    fun `caret shapes of the small font fall back to the line caret on a bigger font`() {
        fun glyphs(scale: TextScale, style: CaretStyle) = PlainTextComponentSerializer.plainText().serialize(
            HudRenderer.render(
                HudState.Typing(emptyList(), emptyList(), listOf(HudLine.untyped("hello world")), 0, 3, scale = scale, caretStyle = style),
                DefaultTheme,
            )
        )
        assertTrue(UiFont.CARET_OUTLINE.toString() in glyphs(TextScale.X1, CaretStyle.OUTLINE))
        assertTrue(UiFont.CARET.toString() in glyphs(TextScale.X2, CaretStyle.OUTLINE))
        assertTrue(UiFont.CARET.toString() in glyphs(TextScale.X1_5, CaretStyle.BLOCK))
    }

    @Test
    fun `the pack has a font per scale with the right advance`() {
        val files = PackBuilder.build()
        for (scale in TextScale.entries) {
            val json = files["assets/blockytype/font/${scale.font.value()}.json"]
            assertNotNull(json, scale.font.value())
        }
        val small = String(files["assets/blockytype/font/mono.json"]!!)
        val big = String(files["assets/blockytype/font/mono_2.json"]!!)
        fun size(j: String) = Regex("\"size\":\\s*([0-9.]+)").find(j)!!.groupValues[1].toDouble()
        assertEquals(2.0, size(big) / size(small), 1e-6)
        val mid = String(files["assets/blockytype/font/mono_15.json"]!!)
        assertEquals(1.5, size(mid) / size(small), 1e-6)
    }
}
