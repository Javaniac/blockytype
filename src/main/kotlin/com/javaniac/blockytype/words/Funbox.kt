package com.javaniac.blockytype.words

import kotlin.random.Random

/**
 * Monkeytype funbox modes that only change which words are generated (pure word functions). Modes that need
 * animation, key events or the network are not offered. [taker] gives a function that makes the next `n` words.
 */
object Funbox {
    private val WORD_MODES = setOf(
        "rAnDoMcAsE", "sPoNgEcAsE", "capitals", "ALL CAPS", "backwards", "ddoouubblleedd", "rot13", "morse",
        "instant messaging", "nospace", "underscore_spaces",
    )
    private val LIST_MODES = setOf("gibberish", "ascii", "specials", "binary", "hexadecimal", "IPv4", "IPv6", "pseudolang", "polyglot")

    /** `memory` hides the words once the test starts: it is a looks change, the words stay normal. */
    private val PLAIN_MODES = setOf("off", "zipf", "memory")

    fun isKnown(mode: String) = mode in PLAIN_MODES || mode in WORD_MODES || mode in LIST_MODES

    /** Size of the made-up lists the generator draws from. */
    private const val POOL = 500

    /** Chars of the `specials` and `ascii` modes: no `<`, `>` or `\` (they are markup in chat and HUD text). */
    private const val SPECIALS = "!@#$%^&*()_+-=[]{};':\",./?|~`"
    private val ASCII = (33..126).map { it.toChar() }.filter { it !in "<>\\" }

    private const val MAX_WORD = 50
    private const val GLUED_WORDS = 3

    /**
     * The word source of [mode]: [base] is the player's language list, [lists] the other languages (polyglot only).
     * [random] makes it repeatable in tests.
     */
    fun taker(mode: String, base: WordList, random: Random, lists: () -> List<WordList> = { listOf(base) }): (Int) -> List<String> {
        if (mode in LIST_MODES) {
            val pool = when (mode) {
                "polyglot" -> WordList("polyglot", false, lists().flatMap { it.words }.distinct().ifEmpty { base.words })
                else -> WordList(mode, false, List(POOL) { madeUp(mode, base, random) })
            }
            val generator = WordGenerator(pool, random = random, lenient = mode != "polyglot")
            return { n -> generator.take(n) }
        }
        val generator = WordGenerator(base, zipf = mode == "zipf", random = random)
        if (mode !in WORD_MODES) return { n -> generator.take(n) }
        return { n -> List(n) { token(mode, generator, random) } }
    }

    private fun token(mode: String, generator: WordGenerator, random: Random): String = when (mode) {
        "nospace" -> glue(generator, "")
        "underscore_spaces" -> glue(generator, "_")
        "rAnDoMcAsE" -> generator.next().map { if (random.nextBoolean()) it.uppercaseChar() else it.lowercaseChar() }.joinToString("")
        "sPoNgEcAsE" -> generator.next().mapIndexed { i, c -> if (i % 2 == 0) c.lowercaseChar() else c.uppercaseChar() }.joinToString("")
        "capitals" -> generator.next().replaceFirstChar { it.uppercaseChar() }
        "ALL CAPS" -> generator.next().uppercase()
        "backwards" -> generator.next().reversed()
        "ddoouubblleedd" -> generator.next().take(MAX_WORD / 2).map { "$it$it" }.joinToString("")
        "rot13" -> generator.next().map(::rot13).joinToString("")
        "morse" -> morse(generator.next())
        "instant messaging" -> generator.next().let { w -> w.lowercase().filter(Char::isLetterOrDigit).ifEmpty { w } }
        else -> generator.next()
    }

    /** Words glued into one token of at most [MAX_WORD] chars (the test has no spaces to type). */
    private fun glue(generator: WordGenerator, separator: String): String {
        val parts = mutableListOf<String>()
        var length = 0
        repeat(GLUED_WORDS) {
            val w = generator.next()
            val added = w.length + if (parts.isEmpty()) 0 else separator.length
            if (length + added <= MAX_WORD) {
                parts += w
                length += added
            }
        }
        return parts.joinToString(separator).ifEmpty { generator.next() }
    }

    private fun rot13(c: Char): Char = when (c) {
        in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
        in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
        else -> c
    }

    private val MORSE = mapOf(
        'a' to ".-", 'b' to "-...", 'c' to "-.-.", 'd' to "-..", 'e' to ".", 'f' to "..-.", 'g' to "--.", 'h' to "....",
        'i' to "..", 'j' to ".---", 'k' to "-.-", 'l' to ".-..", 'm' to "--", 'n' to "-.", 'o' to "---", 'p' to ".--.",
        'q' to "--.-", 'r' to ".-.", 's' to "...", 't' to "-", 'u' to "..-", 'v' to "...-", 'w' to ".--", 'x' to "-..-",
        'y' to "-.--", 'z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....", '6' to "-....",
        '7' to "--...", '8' to "---..", '9' to "----.",
    )

    /** [word] in Morse code, letters separated by `/`. Chars without a code are skipped; at most 8 letters. */
    fun morse(word: String): String =
        word.lowercase().mapNotNull { MORSE[it] }.take(8).joinToString("/").ifEmpty { "." }

    private fun madeUp(mode: String, base: WordList, random: Random): String = when (mode) {
        "gibberish" -> String(CharArray(random.nextInt(2, 9)) { 'a' + random.nextInt(26) })
        "ascii" -> String(CharArray(random.nextInt(1, 7)) { ASCII[random.nextInt(ASCII.size)] })
        "specials" -> String(CharArray(random.nextInt(1, 5)) { SPECIALS[random.nextInt(SPECIALS.length)] })
        "binary" -> String(CharArray(8) { '0' + random.nextInt(2) })
        "hexadecimal" -> "0x" + String(CharArray(random.nextInt(2, 7)) { "0123456789abcdef"[random.nextInt(16)] })
        "IPv4" -> List(4) { random.nextInt(256) }.joinToString(".")
        "IPv6" -> List(8) { Integer.toHexString(random.nextInt(1, 0x10000)) }.joinToString(":")
        else -> pseudoWord(base, random)
    }

    private val modelCache = HashMap<WordList, Map<Char, List<Char>>>()

    private const val END = '\u0000'
    private const val START = '\u0001'

    /** Letter pair statistics of [base], with start and end markers. */
    private fun model(base: WordList): Map<Char, List<Char>> = synchronized(modelCache) {
        modelCache.getOrPut(base) {
            val follow = HashMap<Char, MutableList<Char>>()
            for (w in base.words) {
                val letters = w.lowercase().filter(Char::isLetter)
                if (letters.length < 2) continue
                var prev = START
                for (c in letters) {
                    follow.getOrPut(prev) { mutableListOf() } += c
                    prev = c
                }
                follow.getOrPut(prev) { mutableListOf() } += END
            }
            follow
        }
    }

    /** A made-up word that looks like the language: each letter follows the previous one like in the list. */
    private fun pseudoWord(base: WordList, random: Random): String {
        val follow = model(base)
        if (follow.isEmpty()) return "lorem"
        repeat(50) {
            val sb = StringBuilder()
            var prev = START
            while (sb.length < 10) {
                val next = follow[prev]?.let { it[random.nextInt(it.size)] } ?: END
                if (next == END) break
                sb.append(next)
                prev = next
            }
            if (sb.length >= 3) return sb.toString()
        }
        return "lorem"
    }
}
