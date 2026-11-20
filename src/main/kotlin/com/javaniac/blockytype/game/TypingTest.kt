package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.HudState

/**
 * One accepted keypress (backspace is not logged). [ms] is the time since the first char, [word] the index of the
 * word it was typed in (the space that commits a word belongs to that word).
 */
data class KeyPress(val ms: Long, val correct: Boolean, val word: Int = 0)

/** What ends a test: a number of words, or a time limit (Monkeytype `words` / `time` modes). */
sealed interface TestMode {
    val label: String

    data class Words(val count: Int) : TestMode {
        override val label get() = "words $count"
    }

    data class Time(val seconds: Int) : TestMode {
        override val label get() = "time $seconds"
    }
}

/**
 * One typing test with Monkeytype word semantics. All times are passed in as nanoTime, so the class is
 * deterministic and testable. In [TestMode.Time] the text grows through [moreWords] (called with the number of
 * words wanted) so at least [AHEAD] words always stay ahead of the typist, and the test ends at the deadline:
 * the owner calls [expire] when it passes, and a key after it ends the test too.
 */
class TypingTest(
    val target: TargetText = TargetText.sample(),
    val mode: TestMode = TestMode.Words(target.words.size),
    val rules: TestRules = TestRules(),
    private val moreWords: ((Int) -> List<String>)? = null,
) {
    enum class State { READY, RUNNING, FINISHED }

    /** [PENALIZED]: refused like [REJECTED] (the box is resynced) but the key counts as a mistake. */
    enum class Outcome { ACCEPTED, UNCHANGED, REJECTED, PENALIZED }

    /** Why the test failed (`expert`, `master`, `min speed`, ...), or null. A failed test is finished. */
    var failure: String? = null
        private set

    var state = State.READY
        private set

    /** Everything typed so far: words separated by single spaces. */
    var typed = ""
        private set

    val keys = mutableListOf<KeyPress>()
    private val snapshots = mutableListOf<Pair<Long, String>>() // (ms, typed)
    private var startNanos = 0L
    private var endNanos = 0L

    private val targetWords get() = target.words

    /** Milliseconds of the running test, or of the whole test once finished. */
    fun elapsedMs(nowNanos: Long): Long = when (state) {
        State.READY -> 0L
        State.RUNNING -> (nowNanos - startNanos) / 1_000_000
        State.FINISHED -> durationMs()
    }

    fun durationMs(): Long = (endNanos - startNanos) / 1_000_000

    private val limitNanos get() = (mode as? TestMode.Time)?.let { it.seconds * 1_000_000_000L }

    /** When a running time test ends (nanoTime), or null in words mode or before the first key. */
    fun deadlineNanos(): Long? = if (state == State.READY) null else limitNanos?.let { startNanos + it }

    /** Whole seconds left in a time test (the full limit before the first key); 0 in words mode. */
    fun remainingSeconds(nowNanos: Long): Int {
        val limit = limitNanos ?: return 0
        val left = if (state == State.READY) limit else limit - (nowNanos - startNanos)
        return if (left <= 0) 0 else ((left + 999_999_999L) / 1_000_000_000L).toInt()
    }

    /** Ends a running time test whose deadline has passed at [nowNanos]; the duration is exactly the limit. */
    fun expire(nowNanos: Long) {
        val deadline = deadlineNanos() ?: return
        if (state != State.RUNNING || nowNanos < deadline) return
        state = State.FINISHED
        endNanos = deadline
    }

    private fun ensureAhead() {
        val more = moreWords ?: return
        if (mode !is TestMode.Time) return
        val missing = AHEAD - (target.words.size - currentWordIndex())
        if (missing > 0) target.append(more(AHEAD))
    }

    /** The typed text at [ms] after the start (last snapshot at or before it). */
    fun typedAt(ms: Long): String = snapshots.lastOrNull { it.first <= ms }?.second ?: ""

    /**
     * Applies the box content [input] (without the `/t ` prefix). Only a single appended char or a deleted
     * tail is valid; anything else (paste, jump) is rejected so the caller can resync the box. When the rules
     * change the text after an accepted key (delete on error), [typed] differs from [input]: resync then too.
     */
    fun apply(input: String, nowNanos: Long): Outcome {
        if (state == State.FINISHED) return Outcome.REJECTED
        expire(nowNanos)
        if (state == State.FINISHED) return Outcome.REJECTED
        if (input == typed) return Outcome.UNCHANGED
        ensureAhead()

        val appended = input.length == typed.length + 1 && input.startsWith(typed)
        val deleted = input.length < typed.length && typed.startsWith(input)
        if (!appended && !deleted) return Outcome.REJECTED

        if (deleted) {
            if (rules.confidence == TestRules.Confidence.MAX || input.length < lockedLength()) return Outcome.REJECTED
            val ms = if (state == State.READY) 0L else (nowNanos - startNanos) / 1_000_000
            typed = input
            snapshots += ms to typed
            return Outcome.ACCEPTED
        }

        val c = input.last()
        if (c.code < 0x20 || c.code == 0x7F) return Outcome.REJECTED
        val index = currentWordIndex()
        val word = targetWords[index]
        val cur = currentWord()
        var submitsLast = false
        if (c == ' ') {
            if (cur.isEmpty()) return if (rules.strictSpace) penalize(nowNanos) else Outcome.REJECTED
            if (index == targetWords.lastIndex) {
                // The last word normally ends the test by itself; without quick end a space submits it.
                if (rules.quickEnd || mode !is TestMode.Words) return Outcome.REJECTED
                submitsLast = true
            } else if (rules.stopOnError == TestRules.StopOnError.WORD && !rules.sameWord(cur, word)) {
                return penalize(nowNanos)
            }
        } else {
            if (cur.length >= word.length + MAX_EXTRA) return Outcome.REJECTED
            if (rules.stopOnError == TestRules.StopOnError.LETTER && !isCorrectKey(c)) return penalize(nowNanos)
        }

        if (state == State.READY) {
            state = State.RUNNING
            startNanos = nowNanos
        }
        val ms = (nowNanos - startNanos) / 1_000_000
        val correct = isCorrectKey(c)
        keys += KeyPress(ms, correct, index)
        if (c != ' ' && cur.isEmpty()) wordStartMs = ms

        if (submitsLast) {
            snapshots += ms to typed
            finishAt(nowNanos, null)
            return Outcome.ACCEPTED
        }
        typed = input
        if (!correct && rules.difficulty == TestRules.Difficulty.MASTER) return failAt(nowNanos, "master", ms)
        if (c == ' ') {
            if (ms > wordStartMs) lastBurst = (cur.length + 1) / 5.0 / ((ms - wordStartMs) / 60000.0)
            if (!rules.sameWord(cur, word) && rules.difficulty == TestRules.Difficulty.EXPERT) return failAt(nowNanos, "expert", ms)
            if (tooSlow(cur.length, ms)) return failAt(nowNanos, "min word burst", ms)
        } else if (!correct && rules.deleteOnError != TestRules.DeleteOnError.OFF) {
            autoDelete()
        }
        snapshots += ms to typed
        if (isComplete()) finishAt(nowNanos, null)
        return Outcome.ACCEPTED
    }

    /** Ms of the first key of the word being typed. */
    private var wordStartMs = 0L

    /** A refused key that still counts as a mistake. */
    private fun penalize(nowNanos: Long): Outcome {
        if (state == State.READY) {
            state = State.RUNNING
            startNanos = nowNanos
        }
        val ms = (nowNanos - startNanos) / 1_000_000
        keys += KeyPress(ms, false, currentWordIndex())
        if (rules.difficulty == TestRules.Difficulty.MASTER) {
            failAt(nowNanos, "master", ms)
            return Outcome.REJECTED
        }
        return Outcome.PENALIZED
    }

    private fun failAt(nowNanos: Long, reason: String, ms: Long): Outcome {
        snapshots += ms to typed
        finishAt(nowNanos, reason)
        return Outcome.ACCEPTED
    }

    private fun finishAt(nowNanos: Long, reason: String?) {
        state = State.FINISHED
        endNanos = nowNanos
        failure = reason
    }

    /** Whether the word of [length] chars that was just submitted at [ms] was typed below the burst limit. */
    private fun tooSlow(length: Int, ms: Long): Boolean {
        if (rules.minBurst == TestRules.MinBurst.OFF) return false
        val took = ms - wordStartMs
        if (took <= 0) return false
        val chars = length + 1
        val limit = if (rules.minBurst == TestRules.MinBurst.FLEX) TestRules.BURST_WPM * minOf(1.0, 5.0 / chars) else TestRules.BURST_WPM
        return chars / 5.0 / (took / 60000.0) < limit
    }

    /** Removes what the delete on error rules take away after a wrong char was typed. */
    private fun autoDelete() {
        val cur = currentWord()
        val hard = rules.deleteOnError == TestRules.DeleteOnError.LETTER_HARD || rules.deleteOnError == TestRules.DeleteOnError.WORD_HARD
        val byWord = rules.deleteOnError == TestRules.DeleteOnError.WORD || rules.deleteOnError == TestRules.DeleteOnError.WORD_HARD
        val first = cur.length == 1
        typed = when {
            first && hard && typed.length > 1 -> typed.dropLast(2) // back into the previous word
            first || byWord -> typed.dropLast(cur.length)
            else -> typed.dropLast(2)
        }
    }

    /**
     * Checks the limits that depend on the clock (min speed, min accuracy). Returns false when the test just failed.
     * Call about once a second while running.
     */
    fun checkMinimums(nowNanos: Long): Boolean {
        if (state != State.RUNNING) return true
        val ms = (nowNanos - startNanos) / 1_000_000
        if (rules.minSpeed > 0 && ms >= MIN_JUDGE_MS) {
            val counts = TestStats.countChars(typed, targetWords, mode is TestMode.Time, rules.lazy)
            if (TestStats.wpm(counts.correctWordChars, ms / 1000.0) < rules.minSpeed) {
                finishAt(nowNanos, "min speed")
                return false
            }
        }
        if (rules.minAccuracy > 0 && keys.size >= MIN_JUDGE_KEYS) {
            if (keys.count { it.correct } * 100.0 / keys.size < rules.minAccuracy) {
                finishAt(nowNanos, "min accuracy")
                return false
            }
        }
        return true
    }

    private fun currentWordIndex() = typed.count { it == ' ' }

    private fun currentWord() = typed.substringAfterLast(' ')

    private fun isCorrectKey(c: Char): Boolean {
        val word = targetWords[currentWordIndex()]
        val cur = currentWord()
        return if (c == ' ') rules.sameWord(cur, word) else cur.length < word.length && rules.same(c, word[cur.length])
    }

    /** Typed text up to this length is locked: it cannot be erased. */
    private fun lockedLength(): Int {
        if (rules.confidence != TestRules.Confidence.OFF) return typed.lastIndexOf(' ') + 1 // only the current word
        if (rules.freedom) return 0
        var pos = 0
        var lock = 0
        val words = typed.split(' ')
        for (i in 0 until words.lastIndex) {
            pos += words[i].length + 1
            if (rules.sameWord(words[i], targetWords[i])) lock = pos
        }
        return lock
    }

    private fun isComplete(): Boolean {
        if (mode !is TestMode.Words || currentWordIndex() != targetWords.lastIndex) return false
        val cur = currentWord()
        return if (rules.quickEnd) cur.length >= targetWords.last().length else rules.sameWord(cur, targetWords.last())
    }

    /** Live wpm shown while typing. */
    fun liveWpm(nowNanos: Long): Int {
        val ms = elapsedMs(nowNanos)
        if (state == State.READY || ms < 500) return 0
        return TestStats.wpm(TestStats.countChars(typed, targetWords, mode is TestMode.Time, rules.lazy).correctWordChars, ms / 1000.0).toInt()
    }

    /** The typing screen with the default look (see [TypingView] for the player's own options). */
    fun toHudState(nowNanos: Long, language: String = "english", view: ViewOptions = ViewOptions()): HudState.Typing =
        TypingView.build(this, nowNanos, language, view)

    /** Speed in wpm of the last submitted word (first key to space), 0 before the first one. */
    var lastBurst = 0.0
        private set

    /** Live wpm as a number (0 in the first half second). */
    fun liveWpmExact(nowNanos: Long): Double {
        val ms = elapsedMs(nowNanos)
        if (state == State.READY || ms < 500) return 0.0
        return TestStats.wpm(TestStats.countChars(typed, targetWords, mode is TestMode.Time, rules.lazy).correctWordChars, ms / 1000.0)
    }

    companion object {
        /** Min speed is judged after this many ms, min accuracy after this many keys. */
        const val MIN_JUDGE_MS = 3000L
        const val MIN_JUDGE_KEYS = 5

        /** Letters a word may be typed past its end (Monkeytype limit). */
        const val MAX_EXTRA = 20

        /** Words a time test keeps generated ahead of the typist (Monkeytype generates 100 and tops up). */
        const val AHEAD = 100
    }
}
