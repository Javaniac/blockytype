package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.UiFont

/** The text a test is typed against: a word list wrapped into display lines. Time tests append words as they go. */
class TargetText(words: List<String>, private val width: Int = UiFont.MAX_LINE_CHARS) {
    /** One display line. [start] is the index of its first char in [text]. */
    class Line(val start: Int, val text: String)

    private val allWords = ArrayList<String>()
    val words: List<String> get() = allWords

    var text: String = ""
        private set
    var lines: List<Line> = emptyList()
        private set

    /** Index in [text] of the first char of each word. */
    var wordStarts: IntArray = IntArray(0)
        private set

    init {
        require(words.isNotEmpty())
        append(words)
    }

    /** Adds [more] words at the end and re-wraps. */
    fun append(more: List<String>) {
        require(more.all { it.isNotEmpty() && ' ' !in it && it.length <= width })
        allWords += more
        rebuild()
    }

    private fun rebuild() {
        text = allWords.joinToString(" ")
        val starts = IntArray(allWords.size)
        var pos = 0
        allWords.forEachIndexed { i, w ->
            starts[i] = pos
            pos += w.length + 1
        }
        wordStarts = starts

        val out = mutableListOf<Line>()
        var lineStart = 0
        val sb = StringBuilder()
        for (i in allWords.indices) {
            val w = allWords[i]
            if (sb.isNotEmpty() && sb.length + 1 + w.length > width) {
                out += Line(lineStart, sb.toString())
                lineStart = starts[i]
                sb.clear()
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
        }
        out += Line(lineStart, sb.toString())
        lines = out
    }

    /**
     * Line and column of the caret before char [index] of [text]. The space that ends a line maps to the
     * end of that line; the next index is the start of the following line.
     */
    fun locate(index: Int): Pair<Int, Int> {
        for ((i, line) in lines.withIndex()) {
            if (index in line.start..line.start + line.text.length) return i to (index - line.start)
        }
        val last = lines.lastIndex
        return last to lines[last].text.length
    }

    companion object {
        /** A fresh copy of the sample text (texts are mutable). */
        fun sample() = TargetText(
            (
                "the quick brown fox jumps over the lazy dog while seven wizards quietly box a jolly hen " +
                    "near the old river and every word counts toward your final wpm"
                ).split(' ')
        )
    }
}
