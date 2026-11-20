package com.javaniac.blockytype.ui

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Geometry and glyph strings of the result graph. The plot is drawn with 1 GU "dot" glyphs of font `ui`:
 * glyph `DOT_BASE + h` is a dot [h] units above the bottom of the plot (see the atlas in `PackBuilder`).
 * A dot advances 2 units, so after each dot a space moves the cursor back and every dot lands on its own column.
 */
object ResultGraph {
    /** Plot size in GUI units. The whole result screen has to fit the smallest GUI (320 x 240). */
    const val W = 270

    /** Plot height. The heatmap and the cursor line have to fit under it (see [ResultLayout]). */
    const val H = 45

    /** Rows of the dot atlas: glyph heights 0 until [ATLAS] exist. */
    const val ATLAS = H

    /** y of the plot top, in units below the screen center. */
    const val TOP = -60

    /** Left edge of the plot, in units from the title origin (leaves 30 units for y labels at x -150). */
    /** Space between the frame and the data (lines, baseline, labels), in units. */
    const val MARGIN = 4

    const val LEFT = -120
    const val LABEL_LEFT = -150

    const val DOT_BASE = 0xE200
    const val DOT_ADVANCE = 2

    /** Total advance of a string of dot glyphs and `ui` spaces. */
    fun advanceOf(text: String): Int = text.sumOf {
        if (it.code in DOT_BASE until DOT_BASE + ATLAS) DOT_ADVANCE else UiFont.spaceAdvance(it.toString())
    }

    /**
     * y axis span above [Graph.floor]: the next multiple of 10 (at least 10) above the highest value, or the next
     * whole number (at least 1) when all values are below 10 (small units like words per second).
     */
    fun yMax(graph: Graph): Int {
        val top = max(graph.wpm.max(), graph.raw.max()) - graph.floor
        if (top < 10 && graph.floor == 0 && max(graph.wpm.max(), graph.raw.max()) < 10) return max(1, ceil(top).toInt())
        return max(10, (ceil(top / 10.0) * 10).toInt())
    }

    /** [g] with every value moved down by its floor, so the axis starts at 0. */
    fun shifted(g: Graph): Graph =
        if (g.floor == 0) g else Graph(g.wpm.map { it - g.floor }, g.raw.map { it - g.floor }, g.errors)

    /** Plot x (0 until [W]) of second index [i] (0-based) out of [n] seconds. */
    fun xOf(i: Int, n: Int): Int = if (n <= 1) W / 2 else MARGIN + (i * (W - 1.0 - 2 * MARGIN) / (n - 1)).roundToInt()

    /** Dot height 0 until [H] of [value] on a 0..[yMax] axis. */
    fun heightOf(value: Double, yMax: Int): Int =
        MARGIN + (value / yMax * (H - 1 - 2 * MARGIN)).roundToInt().coerceIn(0, H - 1 - 2 * MARGIN)

    /**
     * Glyph string of a polyline through [values] (one per second). Columns between samples are linearly
     * interpolated, and each column also covers the vertical gap to the next one so steep lines stay connected.
     */
    fun series(values: List<Double>, yMax: Int): String = layout(seriesDots(values, yMax))

    /** The (column, height) dots of [series]. */
    fun seriesDots(values: List<Double>, yMax: Int): List<Pair<Int, Int>> {
        val n = values.size
        val inner = W - 2 * MARGIN
        val heights = IntArray(inner) { c ->
            if (n == 1) {
                heightOf(values[0], yMax)
            } else {
                val pos = c * (n - 1.0) / (inner - 1)
                val i = min(pos.toInt(), n - 2)
                val f = pos - i
                heightOf(values[i] * (1 - f) + values[i + 1] * f, yMax)
            }
        }
        val dots = mutableListOf<Pair<Int, Int>>() // (x, h)
        for (c in 0 until inner) {
            val x = c + MARGIN
            val from = heights[c]
            val to = if (c + 1 < inner) heights[c + 1] else from
            // Cover the gap up to (but excluding) the next column's own dot.
            val lo = min(from, to)
            val hi = max(from, to)
            val range = if (hi - lo > 1) lo..hi else from..from
            for (h in range) dots += x to h
        }
        return dots
    }

    /** The 3 x 3 block of the cursor on the point of second [i] (0-based) out of [n] with [value]. */
    fun markerDots(i: Int, n: Int, value: Double, yMax: Int): List<Pair<Int, Int>> {
        val x = xOf(i, n)
        val h = heightOf(value, yMax)
        return (x - 1..x + 1).flatMap { cx -> (h - 1..h + 1).map { cy -> cx to cy } }
    }

    fun marker(i: Int, n: Int, value: Double, yMax: Int): String = layout(markerDots(i, n, value, yMax))

    /** A horizontal line at height [h]: a dot every [step] columns. */
    fun hline(h: Int, step: Int = 1): String = layout((0 until W step step).map { it to h })

    /** The plot frame, a dot every [step] units. */
    fun border(step: Int = 2): String = layout(
        (0 until W step step).flatMap { listOf(it to H - 1, it to 0) } +
            (1 until H - 1 step step).flatMap { h -> listOf(0 to h, W - 1 to h) },
    )

    /** Turns (column, height) dots into glyphs and spaces, starting and ending at the title origin. */
    private fun layout(dots: List<Pair<Int, Int>>): String {
        val sb = StringBuilder()
        var cursor = 0
        for ((x, h) in dots) {
            val target = LEFT + x
            sb.append(UiFont.space(target - cursor))
            sb.append((DOT_BASE + h).toChar())
            cursor = target + DOT_ADVANCE
        }
        sb.append(UiFont.space(-cursor))
        return sb.toString()
    }

    /** Labels on the x axis: up to 5 seconds, evenly spaced, as (plot x, text). */
    fun xLabels(graph: Graph): List<Pair<Int, String>> {
        val n = graph.seconds
        val count = min(5, n)
        if (count == 1) return listOf(xOf(0, n) to "1")
        return (0 until count).map { k ->
            val i = (k * (n - 1.0) / (count - 1)).roundToInt()
            xOf(i, n) to "${i + 1}s"
        }.distinctBy { it.first }
    }

    /** Seconds (0-based index) with errors, as plot x. */
    fun errorColumns(graph: Graph): List<Int> =
        graph.errors.withIndex().filter { it.value > 0 }.map { xOf(it.index, graph.seconds) }.distinct()
}
