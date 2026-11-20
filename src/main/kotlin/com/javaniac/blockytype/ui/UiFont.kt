package com.javaniac.blockytype.ui

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor

/** What a HUD marker color stands for (see [UiFont.decode]). */
sealed interface Marker {
    data class Element(val id: Int) : Marker

    data class Caret(val id: Int) : Marker
}

/**
 * Constants and primitives of the HUD title technique (see CLAUDE.md "Rendering plan").
 * Shared with the pack generator, which writes the fonts these values describe.
 */
object UiFont {
    // ---- fonts (assets/blockytype/font/)
    val MONO: Key = Key.key("blockytype", "mono")
    val MONO_BIG: Key = Key.key("blockytype", "mono_big")
    val UI: Key = Key.key("blockytype", "ui")

    /** Advance of one `mono` char in GUI units (0.6 em at size 10). */
    const val MONO_ADV = 6

    /** Advance of one `mono_big` char. */
    const val MONO_BIG_ADV = 18

    /** `ui` square (8 px bitmap + 1). */
    const val SQUARE_ADV = 9

    /** `ui` caret (1 px bitmap + 1). */
    const val CARET_ADV = 2

    /** Longest text line in mono chars: the smallest GUI is 320x240, so 50 chars = 300 units. */
    const val MAX_LINE_CHARS = 50

    // ---- `ui` font glyphs

    // Filled blocks: pill, strip and focus backgrounds. Glyph BLOCK_BASE + k is 2^k wide (advance 2^k + 1) and
    // BLOCK_HEIGHT high, TALL_BASE + k is TALL_HEIGHT high (the focus outline). `block` strings undo the extra unit.
    const val BLOCK_BASE = 0xE110
    const val TALL_BASE = 0xE120
    const val BLOCK_MAX_BIT = 6
    const val BLOCK_HEIGHT = 12
    const val BLOCK_ASCENT = 9
    const val TALL_HEIGHT = 14
    const val TALL_ASCENT = 10

    // The face of a player's skin: glyph FACE_BASE + r is a FACE_PIXEL square in row r of a FACE_SIZE tall cell (see
    // the pack's face.png), so one line of colored glyphs draws a whole row and the row index sets the height.
    const val FACE_BASE = 0xE300
    const val FACE_PIXEL = 3
    const val FACE_SIZE = 8 * FACE_PIXEL

    const val SQUARE = ''
    const val CARET = ''

    /** Caret in the shape of the letter cell: a hollow box and an underline (6 x 11 px bitmaps, advance 7). */
    const val CARET_OUTLINE = '\uE102'
    const val CARET_UNDERLINE = '\uE103'
    const val CARET_BOX_ADV = 7

    /** `NEG_SPACE_BASE + k` has advance -2^k, `POS_SPACE_BASE + k` has +2^k. */
    const val NEG_SPACE_BASE = 0xF800
    const val POS_SPACE_BASE = 0xF810
    const val MAX_SPACE_BIT = 10

    /** Largest single-run space magnitude the glyph set can express in one go is unbounded: runs repeat the big glyph. */
    private const val MAX_SPACE_UNIT = 1 shl MAX_SPACE_BIT

    /** String of `ui` space glyphs whose total advance is [n] (negative = move left). */
    fun space(n: Int): String {
        if (n == 0) return ""
        val base = if (n < 0) NEG_SPACE_BASE else POS_SPACE_BASE
        var rest = kotlin.math.abs(n)
        val sb = StringBuilder()
        for (k in MAX_SPACE_BIT downTo 0) {
            val unit = 1 shl k
            while (rest >= unit) {
                sb.append((base + k).toChar())
                rest -= unit
            }
        }
        check(rest == 0) { "unreachable: space($n) leftover $rest (max unit $MAX_SPACE_UNIT)" }
        return sb.toString()
    }

    /** Total advance of a `ui` space string (inverse of [space]), for sanity checks. */
    fun spaceAdvance(s: String): Int = s.sumOf { c ->
        val code = c.code
        when {
            code in NEG_SPACE_BASE..NEG_SPACE_BASE + MAX_SPACE_BIT -> -(1 shl (code - NEG_SPACE_BASE))
            code in POS_SPACE_BASE..POS_SPACE_BASE + MAX_SPACE_BIT -> 1 shl (code - POS_SPACE_BASE)
            else -> error("not a space glyph: U+%04X".format(code))
        }
    }

    /** Total advance of a [block] string (inverse check for tests). */
    fun blockAdvance(s: String): Int = s.sumOf { c ->
        val code = c.code
        when {
            code in BLOCK_BASE..BLOCK_BASE + BLOCK_MAX_BIT -> (1 shl (code - BLOCK_BASE)) + 1
            code in TALL_BASE..TALL_BASE + BLOCK_MAX_BIT -> (1 shl (code - TALL_BASE)) + 1
            code in NEG_SPACE_BASE..NEG_SPACE_BASE + MAX_SPACE_BIT -> -(1 shl (code - NEG_SPACE_BASE))
            else -> error("not a block or space glyph: U+%04X".format(code))
        }
    }

    /** A filled block [width] units wide: glyphs of power of two widths, each followed by a -1 space. */
    fun block(width: Int, tall: Boolean = false): String {
        require(width > 0) { "block width must be positive: $width" }
        val base = if (tall) TALL_BASE else BLOCK_BASE
        val sb = StringBuilder()
        var rest = width
        for (k in BLOCK_MAX_BIT downTo 0) {
            val unit = 1 shl k
            while (rest >= unit) {
                sb.append((base + k).toChar()).append(space(-1))
                rest -= unit
            }
        }
        return sb.toString()
    }

    // ---- element markers (vertex color low bits, read by text.vsh)

    /** Highest HUD element id: 0..15 are the typing and result screens, 16..31 the menus (page bit). */
    const val MAX_ELEMENT_ID = 31

    /**
     * Marks [color] as belonging to HUD element [id] (0..31): `R & 7 == 3` (and `R & 8 == 0`),
     * `G & 3 = (id >> 2) & 3`, `B & 3 = id & 3`, and `G & 4` set for ids 16..31 (the page bit).
     * Off by at most 12/255 in red, 7 in green, 3 in blue. Never send marked colors outside the HUD title.
     */
    fun mark(color: TextColor, id: Int): TextColor {
        require(id in 0..MAX_ELEMENT_ID) { "element id out of range: $id" }
        val r = (color.red() and 15.inv()) or 3
        val g = (color.green() and 7.inv()) or (id shr 2 and 3) or (if (id >= 16) 4 else 0)
        val b = (color.blue() and 3.inv()) or (id and 3)
        return TextColor.color(r, g, b)
    }

    // ---- menu layout (element ids 16..31). Rows are placed relative to the screen center,
    // so server side hit testing needs no knowledge of the window size. Fits the smallest GUI (320x240).

    const val MENU_HEADER_ID = 16
    const val MENU_ROW_FIRST_ID = 17

    /** The typing screen's progress bar uses the id of the menu row (slot 3) that sits just above the live stats row. */
    const val BAR_ID = MENU_ROW_FIRST_ID + 2
    const val MENU_ROWS = 12
    const val MENU_ROW_LAST_ID = MENU_ROW_FIRST_ID + MENU_ROWS - 1
    const val MENU_FOOTER_ID = 31

    /** Distance of the header / first row / footer from the screen center (negative = above). */
    const val MENU_HEADER_DY = -116
    const val MENU_ROW0_DY = -88
    const val MENU_ROW_PITCH = 15
    const val MENU_FOOTER_DY = 100

    // Keybind hints in the top right corner of the typing screen (ids between the menu rows and footer).
    const val SIDE_FIRST_ID = 29
    const val SIDE_ROWS = 2
    const val SIDE_MARGIN = 8
    /** Y of the first row from the screen top (the config bar is at 14, centered and short). */
    const val SIDE_TOP = 26
    const val SIDE_ROW_PITCH = 12

    /** What a vertex color marks. Mirrors `hudElementId` in text.vsh. */
    fun decode(color: TextColor): Marker? {
        val r = color.red()
        val g = color.green()
        val b = color.blue()
        if (r and 7 != 3) return null
        if (r and 8 == 0) {
            return Marker.Element(((g and 3) shl 2) or (b and 3) or (if (g and 4 != 0) 16 else 0))
        }
        return Marker.Caret(((g and 3) shl 2) or (b and 3))
    }

    /**
     * Marks the caret glyph: like [mark] plus `R & 8` (caret), `G & 4` (animate) and `B & 4` (came from the left).
     * Off by at most 15/255 in red, 7 in green and blue.
     */
    fun markCaret(color: TextColor, id: Int, move: CaretMove): TextColor {
        require(id in 0..15) { "element id out of range: $id" }
        val r = (color.red() and 15.inv()) or 3 or 8
        val g = (color.green() and 7.inv()) or (id shr 2 and 3) or (if (move != CaretMove.NONE) 4 else 0)
        val b = (color.blue() and 7.inv()) or (id and 3) or (if (move == CaretMove.FROM_LEFT) 4 else 0)
        return TextColor.color(r, g, b)
    }

    /** The caret glyph, already marked by [markCaret] (HUD components never have shadows). */
    fun caretText(color: TextColor, id: Int, move: CaretMove, glyph: Char = CARET): Component =
        Component.text(glyph.toString()).color(markCaret(color, id, move)).font(UI).shadowColor(ShadowColor.none())

    // ---- components

    /** Marked text in font [font] with no shadow (HUD components never have shadows). */
    fun text(text: String, color: TextColor, id: Int, font: Key = MONO): Component =
        Component.text(text).color(mark(color, id)).font(font).shadowColor(ShadowColor.none())

    /** Unmarked `ui` space component, or null for 0. */
    fun spaceComponent(n: Int): Component? =
        if (n == 0) null else Component.text(space(n)).font(UI)
}
