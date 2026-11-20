package com.javaniac.blockytype.game

import java.text.Normalizer

/**
 * The typing rules a player chose (behavior and input settings), fixed for one test. [from] reads them from the
 * effective settings; the defaults are the plain Monkeytype test.
 */
data class TestRules(
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Correct words may be erased. */
    val freedom: Boolean = false,
    /** A space on an empty word is an error instead of being ignored. */
    val strictSpace: Boolean = false,
    val stopOnError: StopOnError = StopOnError.OFF,
    val deleteOnError: DeleteOnError = DeleteOnError.OFF,
    val confidence: Confidence = Confidence.OFF,
    /** Words mode: the test ends with the last char of the last word, right or wrong. */
    val quickEnd: Boolean = true,
    /** Accents and diacritics may be typed as their plain letter. */
    val lazy: Boolean = false,
    /** Fails below this wpm (0 = off). */
    val minSpeed: Int = 0,
    /** Fails below this accuracy in percent (0 = off). */
    val minAccuracy: Int = 0,
    val minBurst: MinBurst = MinBurst.OFF,
) {
    enum class Difficulty { NORMAL, EXPERT, MASTER }

    enum class StopOnError { OFF, WORD, LETTER }

    enum class DeleteOnError { OFF, LETTER, LETTER_HARD, WORD, WORD_HARD }

    enum class Confidence { OFF, ON, MAX }

    enum class MinBurst { OFF, FIXED, FLEX }

    /** Whether a typed char [typed] counts as the target char [target]. */
    fun same(typed: Char, target: Char): Boolean = typed == target || (lazy && fold(typed) == fold(target))

    /** Whether a typed word equals its target word. */
    fun sameWord(typed: String, target: String): Boolean =
        typed == target || (lazy && typed.length == target.length && typed.indices.all { same(typed[it], target[it]) })

    companion object {
        /** Wpm a word must reach with `min-word-burst` on. */
        const val BURST_WPM = 30.0

        private val EXTRA_FOLDS = mapOf('ø' to 'o', 'Ø' to 'O', 'đ' to 'd', 'Đ' to 'D', 'ł' to 'l', 'Ł' to 'L', 'ħ' to 'h', 'ı' to 'i')

        /** [c] without its accent: `é` to `e`. Chars without a base letter stay. */
        fun fold(c: Char): Char {
            EXTRA_FOLDS[c]?.let { return it }
            if (c.code < 0x80) return c
            return Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first()
        }

        fun from(effective: (String) -> String): TestRules = TestRules(
            difficulty = when (effective("difficulty")) {
                "expert" -> Difficulty.EXPERT
                "master" -> Difficulty.MASTER
                else -> Difficulty.NORMAL
            },
            freedom = effective("freedom-mode") == "on",
            strictSpace = effective("strict-space") == "on",
            stopOnError = when (effective("stop-on-error")) {
                "word" -> StopOnError.WORD
                "letter" -> StopOnError.LETTER
                else -> StopOnError.OFF
            },
            deleteOnError = when (effective("delete-on-error")) {
                "letter" -> DeleteOnError.LETTER
                "letter hard" -> DeleteOnError.LETTER_HARD
                "word" -> DeleteOnError.WORD
                "word hard" -> DeleteOnError.WORD_HARD
                else -> DeleteOnError.OFF
            },
            confidence = when (effective("confidence-mode")) {
                "on" -> Confidence.ON
                "max" -> Confidence.MAX
                else -> Confidence.OFF
            },
            quickEnd = effective("quick-end") != "off",
            lazy = effective("lazy-mode") == "on",
            minSpeed = effective("min-speed").toIntOrNull() ?: 0,
            minAccuracy = effective("min-accuracy").toIntOrNull() ?: 0,
            minBurst = when (effective("min-word-burst")) {
                "fixed" -> MinBurst.FIXED
                "flex" -> MinBurst.FLEX
                else -> MinBurst.OFF
            },
        )
    }
}
