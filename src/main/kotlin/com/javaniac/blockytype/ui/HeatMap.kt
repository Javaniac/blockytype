package com.javaniac.blockytype.ui

import net.kyori.adventure.text.format.TextColor
import kotlin.math.roundToInt

object ColorBlend {
    /** [a] mixed with [b]: 0 = all [a], 1 = all [b]. */
    fun blend(a: TextColor, b: TextColor, t: Double): TextColor {
        fun mix(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return TextColor.color(mix(a.red(), b.red()), mix(a.green(), b.green()), mix(a.blue(), b.blue()))
    }
}

/** A word of the result heatmap: its color role and whether the graph cursor points at it. */
data class HeatWord(val text: String, val role: Role, val highlighted: Boolean)

/** A [word] placed in the heatmap: [index] in the test, [col] chars from the line start. */
data class HeatPlaced(val index: Int, val col: Int, val word: HeatWord)

/** Word wrapping and the scrolling window of the heatmap. */
object HeatMap {
    private const val DOT = '·'
    private const val DIVIDER_WIDTH = 49

    /**
     * A row of centered dots that separates the stats from the heatmap. With an [info] text (the graph cursor) it
     * sits in the middle of the row between two spaces, in the main color.
     */
    fun divider(info: String?): List<Run> {
        if (info == null) return listOf(Run(dots(DIVIDER_WIDTH, fromEnd = false), Role.SUB))
        val text = info.take(DIVIDER_WIDTH - 6)
        val avail = DIVIDER_WIDTH - text.length - 2
        val left = avail / 2
        return listOf(
            Run(dots(left, fromEnd = false), Role.SUB),
            Run(" $text ", Role.MAIN),
            Run(dots(avail - left, fromEnd = true), Role.SUB),
        )
    }

    /** [n] chars of alternating dots and spaces: a dot at every even index counted from the start (or the end). */
    private fun dots(n: Int, fromEnd: Boolean): String =
        (0 until n).joinToString("") { i -> if ((if (fromEnd) n - 1 - i else i) % 2 == 0) DOT.toString() else " " }

    /** All words wrapped at [width] chars (words are never split, one space between them). */
    private fun wrap(words: List<HeatWord>, width: Int): List<List<HeatPlaced>> {
        val lines = mutableListOf<MutableList<HeatPlaced>>()
        var col = 0
        for ((i, w) in words.withIndex()) {
            val line = lines.lastOrNull()
            if (line == null || col + 1 + w.text.length > width) {
                lines += mutableListOf(HeatPlaced(i, 0, w))
                col = w.text.length
            } else {
                line += HeatPlaced(i, col + 1, w)
                col += 1 + w.text.length
            }
        }
        return lines
    }

    /**
     * The [maxLines] lines to show. Without a [focus] the first ones; with one the window starts a line above the
     * line of that word (so it sits on the second row) and stops at the end of the text.
     */
    fun layout(words: List<HeatWord>, width: Int, maxLines: Int, focus: Int?): List<List<HeatPlaced>> {
        val lines = wrap(words, width)
        val focusLine = if (focus == null) 0 else lines.indexOfFirst { l -> l.any { it.index == focus } }.coerceAtLeast(0)
        val start = (focusLine - 1).coerceIn(0, (lines.size - maxLines).coerceAtLeast(0))
        return lines.drop(start).take(maxLines)
    }
}
