package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.TextScale
import com.javaniac.blockytype.ui.UiFont
import com.javaniac.blockytype.words.LanguageDownloader
import com.javaniac.blockytype.words.LanguageRegistry
import com.javaniac.blockytype.words.BritishEnglish
import com.javaniac.blockytype.words.Funbox
import com.javaniac.blockytype.words.WordList
import kotlin.random.Random

/** Builds a fresh test from a player's settings (`test-mode`, `word-count`, `time-limit`, `language`, `funbox`). */
object TestFactory {
    /** A test and the language it uses ([language] is the list id, [displayLanguage] what the HUD shows). */
    class Created(val test: TypingTest, val language: String) {
        val displayLanguage: String get() = LanguageRegistry.entry(language)?.display ?: language
    }

    /** Settings that change the test: when one changes the player's current test is replaced. */
    val SETTINGS = listOf(
        "test-mode", "word-count", "time-limit", "language", "funbox", "british-english",
        "difficulty", "freedom-mode", "strict-space", "stop-on-error", "delete-on-error", "confidence-mode",
        "quick-end", "lazy-mode", "min-speed", "min-accuracy", "min-word-burst", "max-line-width", "font-size",
    )

    fun create(effective: (String) -> String, random: Random = Random.Default): Created {
        val requested = effective("language")
        val language = if (LanguageRegistry.isAvailable(requested)) requested else LanguageRegistry.DEFAULT
        if (!LanguageRegistry.isAvailable(language)) LanguageDownloader.ensureDefault() // first download failed at startup: try again
        val rules = TestRules.from(effective)
        val width = (effective("max-line-width").toIntOrNull() ?: UiFont.MAX_LINE_CHARS).coerceIn(10, TextScale.of(effective("font-size")).maxChars)
        var list = LanguageRegistry.load(language)
        if (effective("british-english") == "on" && language.startsWith("english")) {
            list = WordList(list.name, list.orderedByFrequency, list.words.map(BritishEnglish::convert))
        }
        val taker = Funbox.taker(effective("funbox"), list, random) { polyglotLists(list, random) }
        val take = { n: Int -> taker(n).map { it.take(width) } } // a word never outgrows a line
        val test = if (effective("test-mode") == "time") {
            val seconds = effective("time-limit").toIntOrNull()?.takeIf { it > 0 } ?: 30
            TypingTest(TargetText(take(TypingTest.AHEAD), width), TestMode.Time(seconds), rules) { take(it) }
        } else {
            val count = effective("word-count").toIntOrNull()?.takeIf { it > 0 } ?: 25
            TypingTest(TargetText(take(count), width), TestMode.Words(count), rules)
        }
        return Created(test, language)
    }

    /** Up to [POLYGLOT_LISTS] other languages that are on disk, plus [own], for the polyglot funbox. */
    private fun polyglotLists(own: WordList, random: Random): List<WordList> {
        val others = LanguageRegistry.names().filter { LanguageRegistry.isAvailable(it) && it != own.name }.shuffled(random).take(POLYGLOT_LISTS)
        return listOf(own) + others.mapNotNull { runCatching { LanguageRegistry.load(it) }.getOrNull() }
    }

    private const val POLYGLOT_LISTS = 4
}
