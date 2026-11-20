package com.javaniac.blockytype.game

import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/** Character classes of a typed text against the target (Monkeytype `countChars`). */
data class CharCounts(
    val correct: Int,
    val incorrect: Int,
    val extra: Int,
    val missed: Int,
    /** Chars of fully correct words, plus their trailing space when committed. Basis of wpm. */
    val correctWordChars: Int,
    /** Every typed char incl. committed spaces. Basis of raw. */
    val rawChars: Int,
)

/** One second of the test. */
data class SecondStat(val wpm: Double, val raw: Double, val errors: Int)

data class TestResult(
    val wpm: Double,
    val raw: Double,
    /** Percent 0..100. */
    val accuracy: Double,
    val counts: CharCounts,
    val consistency: Double,
    val seconds: Double,
    val perSecond: List<SecondStat>,
)

/** Pure metric functions; definitions follow Monkeytype (see CLAUDE.md). */
object TestStats {
    fun wpm(chars: Int, seconds: Double): Double = if (seconds <= 0.0) 0.0 else chars / 5.0 / (seconds / 60.0)

    /**
     * Compares [typed] (words separated by single spaces, the last one may be unfinished) with [target].
     * Words are compared char by char; a space after a correct word counts as correct. With [creditPartial]
     * (time mode) the correct prefix of the unfinished last word counts towards [CharCounts.correctWordChars].
     */
    fun countChars(typed: String, target: List<String>, creditPartial: Boolean = false, lazy: Boolean = false): CharCounts {
        val rules = if (lazy) TestRules(lazy = true) else TestRules()
        var letters = 0 // matching letters
        var correctSpaces = 0
        var spaces = 0
        var incorrect = 0
        var extra = 0
        var missed = 0
        var correctWordChars = 0
        val words = typed.split(' ')
        for ((i, typedWord) in words.withIndex()) {
            val t = target.getOrNull(i) ?: break
            val committed = i < words.lastIndex
            val overlap = minOf(typedWord.length, t.length)
            var matched = 0
            for (j in 0 until overlap) if (rules.same(typedWord[j], t[j])) matched++
            letters += matched
            incorrect += overlap - matched
            extra += maxOf(0, typedWord.length - t.length)
            if (committed) {
                missed += maxOf(0, t.length - typedWord.length)
                spaces++
            }
            if (rules.sameWord(typedWord, t)) {
                correctWordChars += t.length
                if (committed) {
                    correctSpaces++
                    correctWordChars++
                }
            } else if (creditPartial && !committed && typedWord.length <= t.length && rules.sameWord(typedWord, t.substring(0, typedWord.length))) {
                correctWordChars += typedWord.length // timed tests credit the correct start of the last word
            }
        }
        return CharCounts(
            correct = letters + correctSpaces,
            incorrect = incorrect,
            extra = extra,
            missed = missed,
            correctWordChars = correctWordChars,
            rawChars = letters + incorrect + extra + spaces,
        )
    }

    /** `kogasa(cov) = 100 * (1 - tanh(cov + cov^3 / 3 + cov^5 / 5))`. */
    fun kogasa(cov: Double): Double = 100.0 * (1.0 - tanh(cov + cov.pow(3) / 3.0 + cov.pow(5) / 5.0))

    /** Consistency of per-second bursts: population standard deviation over mean. */
    fun consistency(bursts: List<Double>): Double {
        if (bursts.isEmpty()) return 0.0
        val mean = bursts.average()
        if (mean == 0.0) return 0.0
        val sd = sqrt(bursts.sumOf { (it - mean).pow(2) } / bursts.size)
        return kogasa(sd / mean)
    }

    /** Final result of a finished [test]. */
    fun result(test: TypingTest): TestResult {
        val durationMs = maxOf(test.durationMs(), 1L)
        val seconds = durationMs / 1000.0
        val credit = test.mode is TestMode.Time
        val counts = countChars(test.typed, test.target.words, credit, test.rules.lazy)

        val keys = test.keys
        val keyCount = keys.size
        val accuracy = if (keyCount == 0) 0.0 else keys.count { it.correct } * 100.0 / keyCount

        val buckets = maxOf(1, kotlin.math.ceil(seconds).toInt())
        val perSecond = (0 until buckets).map { b ->
            val from = b * 1000L
            val to = minOf((b + 1) * 1000L, durationMs)
            val inBucket = keys.filter { it.ms >= from && (it.ms < to || (b == buckets - 1 && it.ms <= to)) }
            val bucketSeconds = maxOf(to - from, 1L) / 1000.0
            val cumulative = countChars(test.typedAt(to), test.target.words, credit, test.rules.lazy)
            SecondStat(
                wpm = wpm(cumulative.correctWordChars, to / 1000.0),
                raw = wpm(inBucket.size, bucketSeconds),
                errors = inBucket.count { !it.correct },
            )
        }

        return TestResult(
            wpm = wpm(counts.correctWordChars, seconds),
            raw = wpm(counts.rawChars, seconds),
            accuracy = accuracy,
            counts = counts,
            consistency = consistency(perSecond.map { it.raw }),
            seconds = seconds,
            perSecond = perSecond,
        )
    }
}
