package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.Graph
import com.javaniac.blockytype.ui.GraphCursor
import com.javaniac.blockytype.ui.HeatMap
import com.javaniac.blockytype.ui.HeatWord
import com.javaniac.blockytype.ui.ResultLayout
import com.javaniac.blockytype.ui.UiFont
import com.javaniac.blockytype.ui.HudState
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.Run
import com.javaniac.blockytype.ui.StatColumn
import kotlin.math.roundToInt

/** Turns a finished test into the result HUD. */
object ResultView {
    fun modeName(test: TypingTest, language: String) = "${test.mode.label} $language"

    /** Word of each heat level; a word with a mistake is drawn in the error color. */
    private fun heatWords(test: TypingTest, highlighted: IntRange?): List<HeatWord> {
        val levels = WordBurst.levels(WordBurst.bursts(test))
        val typed = test.typed.split(' ')
        return test.target.words.mapIndexed { i, word ->
            val role = when {
                levels[i] == WordBurst.UNTYPED -> Role.SUB
                typed.getOrNull(i) != word -> Role.ERROR
                levels[i] == WordBurst.NO_HEAT -> Role.TEXT
                else -> Role.entries[Role.HEAT0.ordinal + levels[i]]
            }
            HeatWord(word, role, highlighted != null && i in highlighted)
        }
    }

    private fun graph(r: TestResult, unit: SpeedUnit, startAtZero: Boolean): Graph {
        val wpm = r.perSecond.map { unit.convert(it.wpm) }
        val raw = r.perSecond.map { unit.convert(it.raw) }
        val errors = r.perSecond.map { it.errors }
        if (startAtZero) return Graph(wpm, raw, errors)
        val lowest = minOf(wpm.min(), raw.min())
        val top = maxOf(wpm.max(), raw.max())
        val step = if (top < 10) 1 else 10
        val floor = (kotlin.math.floor(lowest / step) * step).toInt().coerceAtLeast(0)
        return Graph(wpm, raw, errors, if (floor < top) floor else 0)
    }

    /**
     * [cursor] is the 0-based second the player scrolled to (null = none): it adds the info line and highlights the
     * words typed in that second.
     */
    fun toHudState(
        r: TestResult, test: TypingTest, keys: Keybinds = Keybinds.DEFAULT, cursor: Int? = null, view: ViewOptions = ViewOptions(),
    ): HudState.Result {
        val unit = view.unit
        fun speed(wpm: Double) = unit.format(wpm, view.decimals)
        fun decimal(v: Double, suffix: String) = if (view.decimals) "%.2f%s".format(java.util.Locale.ROOT, v, suffix) else "${v.roundToInt()}$suffix"
        val c = r.counts
        val seconds = r.perSecond.size
        val at = cursor?.takeIf { it in 0 until seconds }
        val highlighted = at?.let { WordBurst.wordsInSecond(test, it + 1, seconds) }
        val graphCursor = at?.let {
            val sec = r.perSecond[it]
            GraphCursor(it, unit.convert(sec.wpm), unit.convert(sec.raw), "${it + 1}s  ${unit.id} ${speed(sec.wpm)}  raw ${speed(sec.raw)}  err ${sec.errors}")
        }
        return HudState.Result(
            big = listOf(
                StatColumn(unit.id, speed(r.wpm)),
                StatColumn("acc", decimal(r.accuracy, "%")),
            ),
            stats = listOf(
                StatColumn("test", test.mode.label),
                StatColumn("raw", speed(r.raw)),
                StatColumn("characters", "${c.correct}/${c.incorrect}/${c.extra}/${c.missed}"),
                StatColumn("consistency", decimal(r.consistency, "%")),
                StatColumn("time", decimal(r.seconds, "s")),
            ),
            graph = graph(r, unit, view.startGraphsAtZero),
            footer = Keybinds.join(
                listOf(
                    listOf(Run(keys.restart, Role.MAIN), Run(" restart", Role.SUB)),
                    listOf(Run(keys.menu, Role.MAIN), Run(" settings", Role.SUB)),
                    listOf(Run("${keys.left}/${keys.right}", Role.MAIN), Run(" scroll", Role.SUB)),
                )
            ),
            heat = HeatMap.layout(heatWords(test, highlighted), UiFont.MAX_LINE_CHARS, ResultLayout.HEAT_LINES, highlighted?.first),
            cursor = graphCursor,
        )
    }
}
