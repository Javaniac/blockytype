package com.javaniac.blockytype.game

import kotlin.math.roundToLong

/** Per-word speed for the result heatmap (Monkeytype `computeBurst` and its percentile levels). */
object WordBurst {
    /** Level of a word whose burst is infinite (no duration, or 1000 wpm and more): no heat color. */
    const val NO_HEAT = -1

    /** Level of a word that was not typed. */
    const val UNTYPED = -2

    private const val INFINITE_FROM = 1000.0
    private val PERCENTILES = doubleArrayOf(0.15, 0.35, 0.65, 0.85)

    /** Number of words that have typed chars. */
    fun typedWords(test: TypingTest): Int = test.typed.split(' ').count { it.isNotEmpty() }

    /**
     * Burst of each target word in wpm: `(typed length + 1) / 5` per minute from the word's first to its last key
     * (the space that commits it is one of them). Null = not typed, infinity = no measurable duration.
     */
    fun bursts(test: TypingTest): List<Double?> {
        val typedWords = test.typed.split(' ')
        val byWord = test.keys.groupBy { it.word }
        return test.target.words.indices.map { i ->
            val len = typedWords.getOrNull(i)?.length ?: 0
            val keys = byWord[i]
            if (len == 0 || keys.isNullOrEmpty()) return@map null
            val ms = keys.last().ms - keys.first().ms
            if (ms <= 0) return@map Double.POSITIVE_INFINITY
            ((len + 1) / 5.0 / (ms / 60_000.0)).roundToLong().toDouble()
        }
    }

    /** Heat level per word: 0 (slowest) to 4 (fastest) by this test's own percentiles, or [NO_HEAT] / [UNTYPED]. */
    fun levels(bursts: List<Double?>): List<Int> {
        val finite = bursts.filterNotNull().filter { it < INFINITE_FROM }.sorted()
        val limits = PERCENTILES.map { p -> finite.getOrElse((finite.size * p).toInt()) { Double.POSITIVE_INFINITY } }
        return bursts.map { b ->
            when {
                b == null -> UNTYPED
                b >= INFINITE_FROM -> NO_HEAT
                else -> limits.indexOfFirst { b < it }.let { if (it < 0) 4 else it }
            }
        }
    }

    /**
     * The words typed during second [s] (1-based) of a result with [seconds] seconds: keys with
     * `(s-1) * 1000 < ms <= s * 1000`; the last second also takes everything after it. Null if there are none.
     */
    fun wordsInSecond(test: TypingTest, s: Int, seconds: Int): IntRange? {
        val from = (s - 1) * 1000L
        val to = if (s >= seconds) Long.MAX_VALUE else s * 1000L
        val words = test.keys.filter { it.ms > from && it.ms <= to }.map { it.word }
        return if (words.isEmpty()) null else words.min()..words.max()
    }
}
