package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.text.format.TextColor

/** Theme color roles a HUD run can use. Resolved against the player's active theme at render time. */
enum class Role {
    MAIN, TEXT, SUB, SUB_ALT, ERROR, CARET, BG,

    /** Live stats (progress, speed, accuracy, burst): the color the player chose at the chosen opacity, see [Palette]. */
    LIVE,

    /** Heatmap levels, slowest to fastest (see `WordBurst`). */
    HEAT0, HEAT1, HEAT2, HEAT3, HEAT4;

    fun color(theme: AbstractTheme): TextColor = when (this) {
        HEAT0 -> theme.errorColor
        HEAT1 -> if (theme.mainColor == theme.textColor) theme.subColor else ColorBlend.blend(theme.errorColor, theme.textColor, 0.5)
        HEAT2 -> if (theme.mainColor == theme.textColor) ColorBlend.blend(theme.subColor, theme.textColor, 0.5) else theme.textColor
        HEAT3 -> if (theme.mainColor == theme.textColor) theme.textColor else ColorBlend.blend(theme.mainColor, theme.textColor, 0.5)
        HEAT4 -> theme.mainColor
        MAIN, LIVE -> theme.mainColor
        TEXT -> theme.textColor
        SUB -> theme.subColor
        SUB_ALT -> theme.subAltColor
        ERROR -> theme.errorColor
        CARET -> theme.caretColor
        BG -> theme.bgColor
    }
}

/** How the caret is drawn (`caret-style`, `pace-caret-style`). */
enum class CaretStyle {
    OFF, LINE, BLOCK, OUTLINE, UNDERLINE;

    companion object {
        fun of(id: String): CaretStyle = when (id) {
            "off" -> OFF
            "block" -> BLOCK
            "outline" -> OUTLINE
            "underline" -> UNDERLINE
            else -> LINE
        }
    }
}

/** The pace caret: before char [column] of line [line] (index in the shown lines), drawn in [style]. */
data class PaceMark(val line: Int, val column: Int, val style: CaretStyle)

/** A piece of text in one color role. */
data class Run(val text: String, val role: Role)

/** State of one target character in a text line. */
enum class CharState(val role: Role) {
    UNTYPED(Role.SUB),
    CORRECT(Role.TEXT),
    ERROR(Role.ERROR),

    /** Untyped, but marked by the highlight mode. */
    HIGHLIGHT(Role.TEXT),

    /** Typed, shown dimmed (typed effect `fade`). */
    FADED(Role.SUB_ALT),
}

/**
 * How the player's color settings change what a [CharState] or a live stat looks like: [flip] swaps typed and
 * untyped text, a monochrome test ([colorful] off) draws mistakes in the text color, live stats use [liveRole]
 * blended towards the background by [liveOpacity].
 */
data class Palette(
    val flip: Boolean = false,
    val colorful: Boolean = true,
    val liveRole: Role = Role.MAIN,
    val liveOpacity: Double = 0.5,
) {
    fun charRole(state: CharState): Role = when {
        state == CharState.CORRECT && flip -> Role.SUB
        state == CharState.UNTYPED && flip -> Role.TEXT
        state == CharState.ERROR && !colorful -> Role.TEXT
        else -> state.role
    }

    fun liveColor(theme: AbstractTheme): TextColor = ColorBlend.blend(theme.bgColor, liveRole.color(theme), liveOpacity)
}

/** One visible text line (at most [UiFont.MAX_LINE_CHARS] chars). */
data class HudLine(val text: String, val states: List<CharState>) {
    init {
        require(text.length <= UiFont.MAX_LINE_CHARS) { "line longer than ${UiFont.MAX_LINE_CHARS} chars" }
        require(states.size == text.length) { "one state per char" }
    }

    companion object {
        /** All chars untyped. */
        fun untyped(text: String) = HudLine(text, List(text.length) { CharState.UNTYPED })
    }
}

/** What the HUD shows. Pure data: the renderer turns it into a boss bar title. */
sealed interface HudState {
    /**
     * Test in progress: config bar (element 1), live stats (2), up to 3 text lines (3-5), footer (7).
     * The caret sits before char [caretColumn] of line [caretLine], or is hidden when [caretLine] is -1.
     */
    data class Typing(
        val config: List<Run>,
        val live: List<Run>,
        val lines: List<HudLine>,
        val caretLine: Int = 0,
        val caretColumn: Int = 0,
        val footer: List<Run> = emptyList(),
        val caretMove: CaretMove = CaretMove.NONE,
        /** Keybind hints at the right screen edge, one run list per line (at most [UiFont.SIDE_ROWS]). */
        val side: List<List<Run>> = emptyList(),
        val palette: Palette = Palette(),
        /** Progress bar fill 0..1 (live progress style `bar`), or null. */
        val bar: Double? = null,
        val caretStyle: CaretStyle = CaretStyle.LINE,
        val scale: TextScale = TextScale.X1,
        val pace: PaceMark? = null,
    ) : HudState {
        init {
            require(lines.size <= MAX_LINES) { "at most $MAX_LINES lines" }
            require(side.size <= UiFont.SIDE_ROWS) { "at most ${UiFont.SIDE_ROWS} side lines" }
            require(caretLine == -1 || caretLine in lines.indices) { "caret line out of range" }
        }

        companion object {
            const val MAX_LINES = 3
        }
    }

    /**
     * Test finished: big numbers with labels (element 8/11), the graph (9, 6, 13-15) and the stats row (10/12).
     */
    data class Result(
        val big: List<StatColumn>,
        val stats: List<StatColumn>,
        val graph: Graph,
        val footer: List<Run> = emptyList(),
        val heat: List<List<HeatPlaced>> = emptyList(),
        val cursor: GraphCursor? = null,
    ) : HudState

    /**
     * A menu: one [header] row, up to [MenuLayout.BODY_ROWS] body rows (null = empty) and an optional [footer],
     * drawn with element ids 16..31. The [focus]ed cell (keyboard / wheel navigation) gets an outline.
     */
    data class Menu(
        val header: MenuRow,
        val rows: List<MenuRow?>,
        val footer: MenuRow?,
        val focus: Focus? = null,
    ) : HudState {
        init {
            require(rows.size <= MenuLayout.BODY_ROWS) { "at most ${MenuLayout.BODY_ROWS} body rows" }
        }
    }
}

/** A player's face (64 `0xRRGGBB` values, row by row) drawn [x] units from the screen center, [UiFont.FACE_SIZE] units tall. */
class FaceArt(val x: Int, val pixels: IntArray) {
    init {
        require(pixels.size == 64) { "a face has 64 pixels, got ${pixels.size}" }
    }
}

/** The focused cell: [slot] (see [MenuLayout]) and the [cell] index in that row. */
data class Focus(val slot: Int, val cell: Int)

/**
 * One piece of a menu row. A cell with an [action] can be focused and selected. [fill] draws a block of that
 * color behind it, [pad] chars of padding on each side (inside the block), [big] uses the big font and [at]
 * places it at a fixed x (GUI units from the screen center) instead of after the previous cell.
 */
data class Cell(
    val text: String,
    val role: Role,
    val action: String? = null,
    val fill: Role? = null,
    val pad: Int = 0,
    val big: Boolean = false,
    val at: Int? = null,
) {
    val adv: Int get() = if (big) UiFont.MONO_BIG_ADV else UiFont.MONO_ADV
    val width: Int get() = (text.length + 2 * pad) * adv
}

/**
 * Cells left to right, [gap] units apart, the first one starting [left] units from the screen center.
 * A [strip] draws a block of that color under the whole row width.
 */
data class MenuRow(
    val cells: List<Cell>,
    val left: Int,
    val gap: Int = GAP,
    val strip: Role? = null,
    val face: FaceArt? = null,
) {
    fun cellWidths(): List<Int> = cells.map { it.width }

    fun cellLefts(): List<Int> {
        var x = left
        return cells.map { c ->
            val start = c.at ?: x
            x = start + c.width + gap
            start
        }
    }

    companion object {
        /** Default space between cells (two mono chars). */
        const val GAP = 2 * UiFont.MONO_ADV

        /** Width the strip covers: the smallest GUI minus margins. */
        const val STRIP_WIDTH = 300

        /** A row centered on the screen center. */
        fun centered(cells: List<Cell>): MenuRow {
            val total = cells.sumOf { it.width } + GAP * (cells.size - 1).coerceAtLeast(0)
            return MenuRow(cells, -total / 2)
        }
    }
}

/** The second the player scrolled to on the result graph: its values and the text of the info line. */
data class GraphCursor(val index: Int, val wpm: Double, val raw: Double, val info: String)

/** A value with a small label above it. */
data class StatColumn(val label: String, val value: String)

/**
 * Per-second series of the result graph. [wpm] and [raw] have one value per second (in the player's speed unit),
 * [errors] the errors in it. [floor] is the value at the bottom of the axis (0 unless the graph does not start at zero).
 */
data class Graph(val wpm: List<Double>, val raw: List<Double>, val errors: List<Int>, val floor: Int = 0) {
    init {
        require(wpm.isNotEmpty() && wpm.size == raw.size && raw.size == errors.size)
    }

    val seconds: Int get() = wpm.size
}
