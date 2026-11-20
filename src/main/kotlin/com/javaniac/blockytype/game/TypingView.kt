package com.javaniac.blockytype.game

import com.javaniac.blockytype.db.PlayerStats
import com.javaniac.blockytype.ui.CaretStyle
import com.javaniac.blockytype.ui.CharState
import com.javaniac.blockytype.ui.HudLine
import com.javaniac.blockytype.ui.HudState
import com.javaniac.blockytype.ui.PaceMark
import com.javaniac.blockytype.ui.Palette
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.Run
import com.javaniac.blockytype.ui.UiFont

/** Builds the typing screen of a [TypingTest] for the player's [ViewOptions]. Pure. */
object TypingView {
    /** Shown instead of a typed letter with typed effect `dots`. */
    private const val DOT = '·'

    fun build(
        test: TypingTest, nowNanos: Long, language: String, o: ViewOptions, stats: PlayerStats? = null, paceWpm: Double? = null,
    ): HudState.Typing {
        val target = test.target
        val words = target.words
        val typedWords = test.typed.split(' ')
        val current = typedWords.lastIndex
        val currentIndex = minOf(current, words.lastIndex)
        val chars = target.text.toCharArray()
        val states = Array(chars.size) { CharState.UNTYPED }
        val rules = test.rules

        for (w in words.indices) {
            val t = words[w]
            val start = target.wordStarts[w]
            val tw = typedWords.getOrNull(w)
            for (j in t.indices) {
                val typed = tw != null && j < tw.length
                val wrong = when {
                    tw == null -> false
                    typed -> !rules.same(tw[j], t[j])
                    else -> w < current // a letter that was never typed in a word that is done
                }
                states[start + j] = when {
                    tw == null || (!typed && w >= current) -> CharState.UNTYPED
                    wrong -> if (o.blind) CharState.CORRECT else CharState.ERROR
                    else -> CharState.CORRECT
                }
                if (typed && wrong && o.showTypos && !o.blind) chars[start + j] = tw!![j]
            }
            // Extra letters are not drawn: flag the word by turning its last char red.
            if (tw != null && tw.length > t.length && !o.hideExtra && !o.blind) states[start + t.length - 1] = CharState.ERROR
            if (start + t.length < states.size) states[start + t.length] = if (w < current) CharState.CORRECT else CharState.UNTYPED
        }

        applyEffect(o.effect, states, chars)
        applyHighlight(o.highlight, test, currentIndex, typedWords.last().length, states)
        if (o.memory && test.state != TypingTest.State.READY) {
            for (i in chars.indices) if (states[i] == CharState.UNTYPED || states[i] == CharState.HIGHLIGHT) chars[i] = ' '
        }

        val caretIndex = target.wordStarts[currentIndex] + minOf(typedWords.last().length, words[currentIndex].length)
        val text = String(chars)
        val lines: List<HudLine>
        val caretLine: Int
        val caretColumn: Int
        val paceIndex = paceIndex(test, nowNanos, o, paceWpm, chars.size)
        var pace: PaceMark? = null
        if (o.tape == ViewOptions.Tape.OFF) {
            val (line, col) = target.locate(caretIndex)
            val first = (line - 1).coerceIn(0, maxOf(0, target.lines.size - HudState.Typing.MAX_LINES))
            lines = target.lines.drop(first).take(HudState.Typing.MAX_LINES).map { l ->
                HudLine(text.substring(l.start, l.start + l.text.length), List(l.text.length) { states[l.start + it] })
            }
            caretLine = line - first
            caretColumn = col
            if (paceIndex != null) {
                val (paceLine, paceCol) = target.locate(paceIndex)
                if (paceLine - first in lines.indices) pace = PaceMark(paceLine - first, paceCol, o.paceStyle)
            }
        } else {
            val margin = (o.width * o.tapeMargin / 100).coerceIn(0, o.width - 1)
            val anchor = if (o.tape == ViewOptions.Tape.LETTER) caretIndex else target.wordStarts[currentIndex]
            val start = anchor - margin
            val window = IntArray(o.width) { start + it }
            lines = listOf(
                HudLine(
                    String(CharArray(o.width) { window[it].let { i -> if (i in text.indices) text[i] else ' ' } }),
                    List(o.width) { window[it].let { i -> if (i in states.indices) states[i] else CharState.UNTYPED } },
                ),
            )
            caretLine = 0
            caretColumn = margin + (caretIndex - anchor)
            if (paceIndex != null && margin + (paceIndex - anchor) in 0 until o.width) {
                pace = PaceMark(0, margin + (paceIndex - anchor), o.paceStyle)
            }
        }

        val committed = current.coerceAtMost(words.size)
        return HudState.Typing(
            config = listOf(
                Run(test.mode.label.substringBefore(' '), Role.MAIN), Run(" ${test.mode.label.substringAfter(' ')}", Role.SUB),
                Run(" | ", Role.SUB), Run(language, Role.SUB),
            ) + averageRuns(o, stats),
            live = liveRuns(test, nowNanos, committed, o),
            lines = lines,
            caretLine = caretLine,
            caretColumn = caretColumn.coerceAtMost(UiFont.MAX_LINE_CHARS),
            palette = Palette(o.flip, o.colorful, o.liveColor, o.liveOpacity),
            bar = if (o.progress == LiveStyle.BAR) progress(test, nowNanos, committed) else null,
            caretStyle = o.caret,
            scale = o.scale,
            pace = pace,
        )
    }

    /** Index in the text the pace caret is before: [paceWpm] times the running time, or null (no pace caret). */
    private fun paceIndex(test: TypingTest, nowNanos: Long, o: ViewOptions, paceWpm: Double?, textLength: Int): Int? {
        if (paceWpm == null || o.paceStyle == CaretStyle.OFF || test.state != TypingTest.State.RUNNING) return null
        val chars = test.elapsedMs(nowNanos) / 1000.0 * paceWpm * 5 / 60
        return minOf(chars.toInt(), textLength)
    }

    /** `avg 104 wpm 96%` of the last 10 tests, when the player asked for it and has results. */
    private fun averageRuns(o: ViewOptions, stats: PlayerStats?): List<Run> {
        val avg = stats?.average ?: return emptyList()
        if (o.average == ViewOptions.AverageShown.OFF) return emptyList()
        val parts = mutableListOf<String>()
        if (o.average != ViewOptions.AverageShown.ACC) parts += "${o.unit.format(avg, o.decimals)} ${o.unit.id}"
        if (o.average != ViewOptions.AverageShown.SPEED) stats.accuracy?.let { parts += "${Math.round(it)}%" }
        return listOf(Run(" | ", Role.SUB), Run("avg " + parts.joinToString(" "), Role.SUB))
    }

    /** Typed letters that are right: hidden, dotted or dimmed. Mistakes always stay visible. */
    private fun applyEffect(effect: ViewOptions.TypedEffect, states: Array<CharState>, chars: CharArray) {
        if (effect == ViewOptions.TypedEffect.KEEP) return
        for (i in states.indices) {
            if (states[i] != CharState.CORRECT) continue
            when (effect) {
                ViewOptions.TypedEffect.HIDE -> chars[i] = ' '
                ViewOptions.TypedEffect.DOTS -> if (chars[i] != ' ') chars[i] = DOT
                ViewOptions.TypedEffect.FADE -> states[i] = CharState.FADED
                ViewOptions.TypedEffect.KEEP -> {}
            }
        }
    }

    /** Marks the untyped letters the highlight mode covers: the next letter, the rest of the word or following words. */
    private fun applyHighlight(mode: ViewOptions.Highlight, test: TypingTest, wordIndex: Int, typedLength: Int, states: Array<CharState>) {
        if (mode == ViewOptions.Highlight.OFF) return
        val target = test.target
        val words = target.words
        fun mark(from: Int, to: Int) {
            for (i in from until minOf(to, states.size)) if (states[i] == CharState.UNTYPED) states[i] = CharState.HIGHLIGHT
        }
        val start = target.wordStarts[wordIndex]
        val letter = start + minOf(typedLength, words[wordIndex].length)
        val wordEnd = start + words[wordIndex].length
        fun following(count: Int) {
            for (w in wordIndex + 1..minOf(wordIndex + count, words.lastIndex)) mark(target.wordStarts[w], target.wordStarts[w] + words[w].length)
        }
        when (mode) {
            ViewOptions.Highlight.LETTER -> mark(letter, letter + 1)
            ViewOptions.Highlight.WORD -> mark(letter, wordEnd)
            ViewOptions.Highlight.NEXT_WORD -> following(1)
            ViewOptions.Highlight.NEXT_TWO -> following(2)
            ViewOptions.Highlight.NEXT_THREE -> following(3)
            ViewOptions.Highlight.OFF -> {}
        }
    }

    private fun progress(test: TypingTest, nowNanos: Long, committed: Int): Double = when (val m = test.mode) {
        is TestMode.Time -> (test.elapsedMs(nowNanos) / (m.seconds * 1000.0)).coerceIn(0.0, 1.0)
        is TestMode.Words -> (committed.toDouble() / m.count).coerceIn(0.0, 1.0)
    }

    /** The live stats row, left to right: progress, speed, accuracy, burst. Each in its style. */
    private fun liveRuns(test: TypingTest, nowNanos: Long, committed: Int, o: ViewOptions): List<Run> {
        val parts = mutableListOf<String>()
        val mode = test.mode
        when (o.progress) {
            LiveStyle.TEXT, LiveStyle.MINI -> parts += when (mode) {
                is TestMode.Time -> test.remainingSeconds(nowNanos).let { if (o.progress == LiveStyle.TEXT) "${it}s" else "$it" }
                is TestMode.Words -> "$committed/${mode.count}"
            }
            else -> {}
        }
        if (o.speed != LiveStyle.OFF) {
            val v = o.unit.format(test.liveWpmExact(nowNanos), o.decimals)
            parts += if (o.speed == LiveStyle.TEXT) "$v ${o.unit.id}" else v
        }
        if (o.accuracy != LiveStyle.OFF && !o.blind) {
            val keys = test.keys
            val acc = if (keys.isEmpty()) 100 else Math.round(keys.count { it.correct } * 100.0 / keys.size).toInt()
            parts += if (o.accuracy == LiveStyle.TEXT) "$acc% acc" else "$acc%"
        }
        if (o.burst != LiveStyle.OFF) {
            val v = o.unit.format(test.lastBurst, o.decimals)
            parts += if (o.burst == LiveStyle.TEXT) "$v ${o.unit.id} burst" else v
        }
        if (parts.isEmpty()) return emptyList()
        return listOf(Run(parts.joinToString("  "), Role.LIVE))
    }
}
