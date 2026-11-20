package com.javaniac.blockytype.words

/**
 * American to British spelling, from Monkeytype's `british-english.ts` (GPL-3.0, commit `78a22fd…`, converted to
 * `british-english.tsv`: lowercase American word, tab, British word). Same rules as Monkeytype's `replace`.
 */
object BritishEnglish {
    private val table: Map<String, String> by lazy {
        val text = BritishEnglish::class.java.getResourceAsStream("/british-english.tsv")?.use { it.readBytes().decodeToString() } ?: ""
        text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    val size: Int get() = table.size

    /** [word] with American spelling in British spelling. Double quotes become single quotes, hyphenated parts are converted one by one. */
    fun convert(word: String): String {
        val w = word.replace('"', '\'')
        if ('-' in w) return w.split('-').joinToString("-") { convert(it) }
        val core = w.trim { !it.isLetterOrDigit() && it != '\'' }.trim('\'')
        if (core.isEmpty()) return w
        val british = table[core.lowercase()] ?: return w
        val replaced = when {
            core.length > 1 && core == core.uppercase() -> british.uppercase()
            core.first().isUpperCase() -> british.replaceFirstChar { it.uppercaseChar() }
            else -> british
        }
        return w.replaceFirst(core, replaced)
    }
}
