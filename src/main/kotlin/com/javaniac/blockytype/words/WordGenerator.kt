package com.javaniac.blockytype.words

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.random.Random

/** A language word list. [orderedByFrequency]: most common words first (only used by Zipf picking). */
class WordList(val name: String, val orderedByFrequency: Boolean, val words: List<String>)

/**
 * Picks the next word like Monkeytype's `getNextWord`: uniformly (or Zipf distributed), redrawing up to
 * [MAX_REDRAWS] times while the word repeats one of the previous two, is "I", has symbols or has digits.
 * Words with spaces or longer than [MAX_WORD] chars cannot be typed in the test and are never used.
 */
class WordGenerator(
    list: WordList,
    private val zipf: Boolean = false,
    private val random: Random = Random.Default,
    /** Made-up lists (funbox) may contain symbols and digits: only repeats are redrawn. */
    private val lenient: Boolean = false,
) {
    private val words = list.words.filter { it.isNotEmpty() && it.length <= MAX_WORD && it.none(Char::isWhitespace) }
    private var previous = ""
    private var previous2 = ""

    init {
        require(words.isNotEmpty()) { "word list '${list.name}' has no usable words" }
    }

    fun next(): String {
        var word = draw()
        var tries = 0
        while (tries < MAX_REDRAWS && rejected(word)) {
            word = draw()
            tries++
        }
        previous2 = previous
        previous = word
        return word
    }

    fun take(n: Int): List<String> = List(n) { next() }

    private fun draw(): String =
        if (zipf) words[zipfIndex(words.size, random.nextDouble()).coerceIn(0, words.lastIndex)]
        else words[random.nextInt(words.size)]

    private fun rejected(w: String): Boolean =
        if (lenient) w == previous || w == previous2 else w == previous || w == previous2 || w == "I" || w.any { it in SYMBOLS } || w.any(Char::isDigit)

    companion object {
        const val MAX_REDRAWS = 100
        const val MAX_WORD = 50
        // Monkeytype also rejects the apostrophe; here it stays, or contractions and many languages would be unusable.
        private const val SYMBOLS = "-=_+[]{};\\:\"|,./<>?"
        private const val EULER_GAMMA = 0.5772156649015329

        /** Monkeytype `zipfyRandomArrayIndex`: inverse CDF of a Zipf distribution over [size] ranks. */
        fun zipfIndex(size: Int, r: Double): Int {
            val h = ln(size + 0.5) + EULER_GAMMA
            return floor(exp(r * h - EULER_GAMMA) - 0.5).toInt()
        }
    }
}
