package com.javaniac.blockytype.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/** Ids of HUD elements (4 bits in the color marker); `text.vsh` `hudAnchor` places each one. */
object Element {
    const val BACKGROUND = 0
    const val CONFIG = 1
    const val LIVE = 2
    const val LINE_FIRST = 3 // 3..5
    const val RESULT_ERRORS = 6
    const val FOOTER = 7
    const val RESULT_BIG = 8
    const val RESULT_GRAPH = 9
    const val RESULT_STATS_LABEL = 10
    const val RESULT_BIG_LABEL = 11
    const val RESULT_STATS = 12
    const val RESULT_X_LABELS = 13
    const val RESULT_Y_MID = 14
    const val RESULT_Y_ZERO = 15

    /** Divider (with the cursor info) and the heatmap lines of the result: menu row ids, a screen never shows both. */
    const val RESULT_DIVIDER = 25
    const val RESULT_HEAT_FIRST = 26 // 26..28
}

/**
 * Vertical positions of the result screen in units below the screen center (GUI 320 x 240 is the smallest).
 * `text.vsh` places the elements with these values, see `Shaders.hudAnchor`.
 */
object ResultLayout {
    const val BIG_DY = -82
    const val BOTTOM = ResultGraph.TOP + ResultGraph.H
    const val ERRORS_DY = BOTTOM - 14
    const val Y_MID_DY = ResultGraph.TOP + ResultGraph.H / 2 - 4
    const val Y_ZERO_DY = BOTTOM - 8 - ResultGraph.MARGIN
    const val X_LABELS_DY = BOTTOM + 4
    const val STATS_LABEL_DY = BOTTOM + 20
    const val STATS_VALUE_DY = BOTTOM + 32
    const val HEAT_LINES = 3
    const val DIVIDER_DY = UiFont.MENU_ROW0_DY + UiFont.MENU_ROW_PITCH * (Element.RESULT_DIVIDER - UiFont.MENU_ROW_FIRST_ID)

    /** The footer is anchored to the screen bottom: `gh - 20`, which is 100 below the center at 240. */
    const val FOOTER_DY = 100
}

/**
 * Builds the boss bar title. Every element is a segment that starts at the title origin and ends back
 * at it (net advance 0), so the whole title is 0 wide and the origin stays exactly
 * (floor(guiWidth / 2), 3). [build] verifies this.
 */
class TitleBuilder(private val resolve: (Role) -> TextColor) {
    private val parts = mutableListOf<Component>()
    private var advance = 0

    private fun space(n: Int) {
        UiFont.spaceComponent(n)?.let { parts += it }
        advance += n
    }

    /** Adds [runs] (adjacent runs of the same role are merged) and advances by `chars * adv`. */
    private fun emit(id: Int, runs: List<Run>, adv: Int, font: Key) {
        var pending: Run? = null
        fun flush() {
            pending?.let { parts += UiFont.text(it.text, resolve(it.role), id, font) }
            pending = null
        }
        for (run in runs) {
            if (run.text.isEmpty()) continue
            val p = pending
            if (p != null && p.role == run.role) {
                pending = Run(p.text + run.text, run.role)
            } else {
                flush()
                pending = run
            }
        }
        flush()
        advance += runs.sumOf { it.text.length } * adv
    }

    /** Element 0: the full-screen background, stretched from the square glyph by the shader. */
    fun background(color: TextColor) = apply {
        parts += UiFont.text(UiFont.SQUARE.toString(), color, Element.BACKGROUND, UiFont.UI)
        advance += UiFont.SQUARE_ADV
        space(-UiFont.SQUARE_ADV)
    }

    /** Centered on the origin. */
    fun centered(id: Int, runs: List<Run>, adv: Int = UiFont.MONO_ADV, font: Key = UiFont.MONO) = apply {
        val width = runs.sumOf { it.text.length } * adv
        val lead = -(width / 2)
        space(lead)
        emit(id, runs, adv, font)
        space(-(width + lead))
    }

    /** Starting [left] units from the origin (negative = left of center). */
    fun leftAligned(id: Int, left: Int, runs: List<Run>, adv: Int = UiFont.MONO_ADV, font: Key = UiFont.MONO) = apply {
        val width = runs.sumOf { it.text.length } * adv
        space(left)
        emit(id, runs, adv, font)
        space(-(left + width))
    }

    /** Like [leftAligned], centered on [cx] units from the origin. */
    fun centeredAt(id: Int, cx: Int, runs: List<Run>, adv: Int = UiFont.MONO_ADV, font: Key = UiFont.MONO) = apply {
        val width = runs.sumOf { it.text.length } * adv
        leftAligned(id, cx - width / 2, runs, adv, font)
    }

    /**
     * Raw `ui` font text (dot glyphs and spaces, see [ResultGraph]) in one color.
     * The strings from [ResultGraph] start and end at the origin; [build] checks the total.
     */
    fun dots(id: Int, role: Role, text: String) = apply {
        if (text.isEmpty()) return@apply
        parts += UiFont.text(text, resolve(role), id, UiFont.UI)
        advance += ResultGraph.advanceOf(text)
    }

    /** Short mono texts at x offsets from the origin, as (left x, text). Overlapping ones are skipped. */
    fun scatter(id: Int, role: Role, items: List<Pair<Int, String>>) = apply {
        var pos = 0 // where the cursor is, relative to the origin
        var end = Int.MIN_VALUE // right edge of the last label, to skip overlapping ones
        for ((x, text) in items.sortedBy { it.first }) {
            if (x < end) continue
            space(x - pos)
            parts += UiFont.text(text, resolve(role), id, UiFont.MONO)
            advance += text.length * UiFont.MONO_ADV
            pos = x + text.length * UiFont.MONO_ADV
            end = pos
        }
        space(-pos)
    }

    /**
     * A text at [x] units from the origin. With a [fill] a block of that color is drawn behind it (the text is
     * inset by [pad] chars), with an [outline] a slightly bigger, taller block of that color behind the fill.
     */
    data class Placed(
        val x: Int,
        val text: String,
        val role: Role,
        val fill: Role? = null,
        val pad: Int = 0,
        val big: Boolean = false,
        val outline: Role? = null,
    ) {
        val adv: Int get() = if (big) UiFont.MONO_BIG_ADV else UiFont.MONO_ADV
        val width: Int get() = (text.length + 2 * pad) * adv
    }

    /** A block of [role] drawn first, [width] units from [x]. */
    data class Strip(val x: Int, val width: Int, val role: Role)

    /**
     * Texts at x offsets from the origin (the boxes must not overlap), each in its own role, optionally on blocks.
     * Everything is drawn in element [id], in order, so later things cover earlier ones.
     */
    fun placed(id: Int, items: List<Placed>, strip: Strip? = null, face: FaceArt? = null) = apply {
        var pos = 0 // where the cursor is, relative to the origin

        fun moveTo(x: Int) {
            space(x - pos)
            pos = x
        }

        fun draw(width: Int, role: Role, tall: Boolean) {
            parts += UiFont.text(UiFont.block(width, tall), resolve(role), id, UiFont.UI)
            advance += width
            pos += width
        }

        strip?.let {
            moveTo(it.x)
            draw(it.width, it.role, tall = false)
        }
        face?.let {
            // One line of glyphs per row of the face: glyph FACE_BASE + r sits r pixels down, each is followed by -1.
            for (r in 0 until 8) {
                moveTo(it.x)
                var c = 0
                while (c < 8) {
                    val color = it.pixels[r * 8 + c]
                    var n = 1
                    while (c + n < 8 && it.pixels[r * 8 + c + n] == color) n++
                    val glyph = (UiFont.FACE_BASE + r).toChar().toString() + UiFont.space(-1)
                    parts += UiFont.text(glyph.repeat(n), TextColor.color(color), id, UiFont.UI)
                    advance += n * UiFont.FACE_PIXEL
                    pos += n * UiFont.FACE_PIXEL
                    c += n
                }
            }
        }
        var end = Int.MIN_VALUE // right edge of the last box
        for (item in items.sortedBy { it.x }) {
            require(item.x >= end) { "placed texts overlap at x=${item.x}" }
            item.outline?.let {
                moveTo(item.x - 1)
                draw(item.width + 2, it, tall = true)
            }
            item.fill?.let {
                moveTo(item.x)
                draw(item.width, it, tall = false)
            }
            if (item.text.isNotEmpty()) {
                moveTo(item.x + item.pad * item.adv)
                parts += UiFont.text(item.text, resolve(item.role), id, if (item.big) UiFont.MONO_BIG else UiFont.MONO)
                advance += item.text.length * item.adv
                pos += item.text.length * item.adv
            }
            end = item.x + item.width
        }
        space(-pos)
    }

    /** A caret glyph at [x] units from the origin, drawn with element [id]. */
    fun caret(
        id: Int, x: Int, color: TextColor, move: CaretMove = CaretMove.NONE,
        glyph: Char = UiFont.CARET, adv: Int = UiFont.CARET_ADV,
    ) = apply {
        space(x)
        parts += UiFont.caretText(color, id, move, glyph)
        advance += adv
        space(-(x + adv))
    }

    fun build(): Component {
        check(advance == 0) { "HUD title must be 0 wide, was $advance" }
        return Component.text("").children(parts)
    }
}
