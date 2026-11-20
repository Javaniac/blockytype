package com.javaniac.blockytype.ui

import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders [HudState] as the title of one invisible boss bar per player.
 * Call from the Minestom tick thread (or via `withMinestomMain`).
 */
object HudRenderer {
    /** Left edge of the text block, in units from the title origin. */
    private const val LEFT = -(UiFont.MAX_LINE_CHARS * UiFont.MONO_ADV) / 2

    /** Rows (stats) are at most this wide: the smallest GUI is 320 units. */
    private const val ROW_WIDTH = 300
    private const val MAX_GAP = 24

    /** Distance of the two big numbers from the center. */
    private const val BIG_OFFSET = 60
    private const val HEAT_LEFT = -150 // 50 chars of 6 units centered

    private val bars = ConcurrentHashMap<UUID, BossBar>()

    /** Pure: builds the title for [state]. */
    fun render(state: HudState, theme: AbstractTheme): Component {
        val palette = (state as? HudState.Typing)?.palette
        val b = TitleBuilder { if (it == Role.LIVE && palette != null) palette.liveColor(theme) else it.color(theme) }
        b.background(theme.bgColor)
        when (state) {
            is HudState.Typing -> typing(b, state, theme)
            is HudState.Result -> result(b, state)
            is HudState.Menu -> menu(b, state)
        }
        return b.build()
    }

    /** The focus outline is the accent color, or the text color on a pill that is already filled with it. */
    fun outlineRole(fill: Role?): Role = if (fill == Role.MAIN) Role.TEXT else Role.MAIN

    private fun menu(b: TitleBuilder, s: HudState.Menu) {
        for (slot in MenuLayout.HEADER..MenuLayout.FOOTER) {
            val row = when (slot) {
                MenuLayout.HEADER -> s.header
                MenuLayout.FOOTER -> s.footer
                else -> s.rows.getOrNull(slot - 1)
            } ?: continue
            val lefts = row.cellLefts()
            val items = row.cells.mapIndexed { i, cell ->
                val focused = s.focus != null && s.focus.slot == slot && s.focus.cell == i
                TitleBuilder.Placed(
                    x = lefts[i], text = cell.text, role = cell.role,
                    fill = if (focused && cell.fill == null) Role.BG else cell.fill,
                    pad = cell.pad, big = cell.big,
                    outline = if (focused) outlineRole(cell.fill) else null,
                )
            }
            val strip = row.strip?.let { TitleBuilder.Strip(row.left, MenuRow.STRIP_WIDTH, it) }
            b.placed(MenuLayout.elementId(slot), items, strip, row.face)
        }
    }

    private fun typing(b: TitleBuilder, s: HudState.Typing, theme: AbstractTheme) {
        b.centered(Element.CONFIG, s.config)
        // The logo sits in the top left corner, in the same element as the config bar.
        b.placed(Element.CONFIG, emptyList(), face = FaceArt(LEFT, Logo.pixels(theme)))
        s.bar?.let { b.placed(UiFont.BAR_ID, bar(it)) }
        b.leftAligned(Element.LIVE, LEFT, s.live)
        s.lines.forEachIndexed { i, line ->
            val id = Element.LINE_FIRST + i
            b.leftAligned(id, LEFT, line.text.indices.map { Run(line.text[it].toString(), s.palette.charRole(line.states[it])) }, s.scale.adv, s.scale.font)
            if (s.pace != null && i == s.pace.line) {
                caret(b, id, s.scale, s.pace.column, s.pace.style, theme.subColor, Role.SUB, CaretMove.NONE, line.text.getOrNull(s.pace.column))
            }
            if (i == s.caretLine) {
                caret(b, id, s.scale, s.caretColumn, s.caretStyle, theme.caretColor, Role.CARET, s.caretMove, line.text.getOrNull(s.caretColumn))
            }
        }
        if (s.footer.isNotEmpty()) b.centered(Element.FOOTER, s.footer)
        s.side.forEachIndexed { i, runs ->
            // Right aligned: the side anchor is the right screen edge (minus a margin).
            b.leftAligned(UiFont.SIDE_FIRST_ID + i, -runs.sumOf { it.text.length } * UiFont.MONO_ADV, runs)
        }
    }

    /** One caret in [style] before char [column] of the line drawn with element [id]; [under] is the char below it (block caret). */
    private fun caret(
        b: TitleBuilder, id: Int, scale: TextScale, column: Int, style: CaretStyle, color: net.kyori.adventure.text.format.TextColor, role: Role,
        move: CaretMove, under: Char?,
    ) {
        val x = LEFT + column * scale.adv
        // The block, outline and underline carets are drawn for the small font; bigger fonts get the line caret.
        val style = if (scale != TextScale.X1 && style != CaretStyle.OFF) CaretStyle.LINE else style
        when (style) {
            CaretStyle.OFF -> {}
            CaretStyle.LINE -> b.caret(id, x - 1, color, move)
            CaretStyle.OUTLINE -> b.caret(id, x, color, move, UiFont.CARET_OUTLINE, UiFont.CARET_BOX_ADV)
            CaretStyle.UNDERLINE -> b.caret(id, x, color, move, UiFont.CARET_UNDERLINE, UiFont.CARET_BOX_ADV)
            // A filled cell with the letter in the background color on it, like Monkeytype's block caret.
            CaretStyle.BLOCK -> b.placed(id, listOf(TitleBuilder.Placed(x, (under ?: ' ').toString(), Role.BG, fill = role)))
        }
    }

    /** The progress bar: a [BAR_CELLS] chars wide strip, the first [fraction] of it in the live color. */
    fun bar(fraction: Double): List<TitleBuilder.Placed> {
        val filled = (fraction.coerceIn(0.0, 1.0) * BAR_CELLS).toInt()
        val cells = mutableListOf<TitleBuilder.Placed>()
        if (filled > 0) cells += TitleBuilder.Placed(LEFT, " ".repeat(filled), Role.TEXT, fill = Role.LIVE)
        if (filled < BAR_CELLS) {
            cells += TitleBuilder.Placed(LEFT + filled * UiFont.MONO_ADV, " ".repeat(BAR_CELLS - filled), Role.TEXT, fill = Role.SUB_ALT)
        }
        return cells
    }

    private const val BAR_CELLS = UiFont.MAX_LINE_CHARS

    /** Left x of each column of a row that is centered as a whole, columns separated by an even gap. */
    private fun columnLefts(widths: List<Int>): List<Int> {
        val gap = if (widths.size < 2) 0 else minOf(MAX_GAP, (ROW_WIDTH - widths.sum()) / (widths.size - 1))
        val total = widths.sum() + gap * (widths.size - 1)
        var x = -total / 2
        return widths.map { w -> x.also { x += w + gap } }
    }

    /** "slow" [5 heat blocks] "fast", centered on the origin: what the heatmap colors mean. */
    fun legend(): List<TitleBuilder.Placed> {
        val adv = UiFont.MONO_ADV
        val step = adv + 2
        val blocks = 5 * adv + 4 * 2
        val slowX = -(4 * adv + adv + blocks + adv + 4 * adv) / 2
        val first = slowX + 4 * adv + adv
        return listOf(TitleBuilder.Placed(slowX, "slow", Role.SUB)) +
            (0 until 5).map { TitleBuilder.Placed(first + it * step, " ", Role.SUB, fill = Role.entries[Role.HEAT0.ordinal + it]) } +
            TitleBuilder.Placed(first + blocks + adv, "fast", Role.SUB)
    }

    private fun result(b: TitleBuilder, s: HudState.Result) {
        if (s.heat.isNotEmpty()) b.placed(Element.CONFIG, legend())
        // Big numbers (wpm, acc) with a small label above, one left and one right of the center.
        val centers = if (s.big.size == 1) listOf(0) else listOf(-BIG_OFFSET, BIG_OFFSET)
        s.big.take(centers.size).forEachIndexed { i, c ->
            b.centeredAt(Element.RESULT_BIG_LABEL, centers[i], listOf(Run(c.label, Role.SUB)))
            b.centeredAt(
                Element.RESULT_BIG, centers[i], listOf(Run(c.value, Role.MAIN)),
                UiFont.MONO_BIG_ADV, UiFont.MONO_BIG,
            )
        }

        graph(b, s.graph, s.cursor)

        // Stats row: label over value, columns sized by their wider text.
        val widths = s.stats.map { maxOf(it.label.length, it.value.length) * UiFont.MONO_ADV }
        val lefts = columnLefts(widths)
        s.stats.forEachIndexed { i, c ->
            val mid = lefts[i] + widths[i] / 2
            b.centeredAt(Element.RESULT_STATS_LABEL, mid, listOf(Run(c.label, Role.SUB)))
            b.centeredAt(Element.RESULT_STATS, mid, listOf(Run(c.value, Role.MAIN)))
        }
        b.centered(Element.RESULT_DIVIDER, HeatMap.divider(s.cursor?.info))
        s.heat.forEachIndexed { i, line ->
            val items = line.map { p ->
                val w = p.word
                TitleBuilder.Placed(HEAT_LEFT + p.col * UiFont.MONO_ADV, w.text, w.role, fill = if (w.highlighted) Role.SUB_ALT else null)
            }
            b.placed(Element.RESULT_HEAT_FIRST + i, items)
        }
        if (s.footer.isNotEmpty()) b.centered(Element.FOOTER, s.footer)
    }

    private fun graph(b: TitleBuilder, original: Graph, cursor: GraphCursor?) {
        val floor = original.floor
        val g = ResultGraph.shifted(original)
        val yMax = ResultGraph.yMax(g)
        // Axes first, then raw, then wpm on top.
        b.dots(Element.RESULT_GRAPH, Role.SUB_ALT, ResultGraph.hline(ResultGraph.MARGIN))
        b.dots(Element.RESULT_GRAPH, Role.SUB, ResultGraph.border())
        b.dots(Element.RESULT_GRAPH, Role.SUB_ALT, ResultGraph.hline(ResultGraph.H / 2, step = 4))
        b.dots(Element.RESULT_GRAPH, Role.SUB, ResultGraph.series(g.raw, yMax))
        b.dots(Element.RESULT_GRAPH, Role.MAIN, ResultGraph.series(g.wpm, yMax))
        // The cursor: one dot on each line at the selected second.
        cursor?.let {
            b.dots(Element.RESULT_GRAPH, Role.SUB, ResultGraph.marker(it.index, g.seconds, it.raw - floor, yMax))
            b.dots(Element.RESULT_GRAPH, Role.MAIN, ResultGraph.marker(it.index, g.seconds, it.wpm - floor, yMax))
        }

        // y labels (max, mid, zero). The max label shares the graph element (anchored at the plot top).
        b.leftAligned(Element.RESULT_GRAPH, ResultGraph.LABEL_LEFT, listOf(Run("${yMax + floor}", Role.SUB)))
        b.leftAligned(Element.RESULT_Y_MID, ResultGraph.LABEL_LEFT, listOf(Run("${yMax / 2 + floor}", Role.SUB)))
        b.leftAligned(Element.RESULT_Y_ZERO, ResultGraph.LABEL_LEFT, listOf(Run("$floor", Role.SUB)))

        // x labels and error markers (plot x converted to title x).
        val half = UiFont.MONO_ADV / 2
        b.scatter(
            Element.RESULT_X_LABELS, Role.SUB,
            ResultGraph.xLabels(g).map { (x, t) -> ResultGraph.LEFT + x - t.length * half to t },
        )
        b.scatter(
            Element.RESULT_ERRORS, Role.ERROR,
            ResultGraph.errorColumns(g).map { x -> ResultGraph.LEFT + x - half to "x" },
        )
    }

    /** Shows or updates [player]'s HUD. */
    fun update(player: Player, state: HudState, theme: AbstractTheme) {
        val title = render(state, theme)
        val existing = bars[player.uuid]
        if (existing != null) {
            existing.name(title)
            return
        }
        val bar = BossBar.bossBar(title, 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
        bars[player.uuid] = bar
        player.showBossBar(bar)
    }

    /** Removes [player]'s HUD (also call on disconnect). */
    fun hide(player: Player) {
        bars.remove(player.uuid)?.let { player.hideBossBar(it) }
    }
}
