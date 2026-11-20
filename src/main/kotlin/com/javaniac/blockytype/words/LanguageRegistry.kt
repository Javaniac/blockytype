package com.javaniac.blockytype.words

import java.nio.file.Files
import java.nio.file.Path

/**
 * Monkeytype word lists (GPL-3.0, see `languages/SOURCE.txt`). The jar only has the index (`languages/index.txt`);
 * [LanguageDownloader] stores each list in `languages/` (english on the first start, the others when a player picks them).
 * The index already excludes code and novelty lists and scripts the fonts cannot show.
 */
object LanguageRegistry {
    const val DEFAULT = "english"

    /** One language list. [bytes] is the size of the original Monkeytype file. */
    data class Entry(val name: String, val bytes: Long, val orderedByFrequency: Boolean) {
        val display: String get() = name.replace('_', ' ')
    }

    /** Where the lists are stored (tests point `blockytype.languages.dir` at fixtures). */
    var dir: Path = Path.of(System.getProperty("blockytype.languages.dir") ?: "languages")

    private val all: List<Entry> by lazy {
        val text = (LanguageRegistry::class.java.getResourceAsStream("/languages/index.txt")
            ?: error("Missing bundled resource /languages/index.txt")).use { it.readBytes().decodeToString() }
        order(parseIndex(text))
    }

    private val byName: Map<String, Entry> by lazy { all.associateBy { it.name } }

    /** `name<TAB>bytes<TAB>orderedByFrequency` per line. */
    fun parseIndex(text: String): List<Entry> = text.lineSequence().filter { it.isNotBlank() }.map { line ->
        val f = line.split('\t')
        Entry(f[0], f[1].toLong(), f[2] == "1")
    }.toList()

    /** `english` first, then the other `english_*` lists, then every other language, each group alphabetical. */
    fun order(entries: List<Entry>): List<Entry> = entries.sortedWith(
        compareBy<Entry> { if (it.name == DEFAULT) 0 else if (it.name.startsWith("${DEFAULT}_")) 1 else 2 }.thenBy { it.name },
    )

    fun entries(): List<Entry> = all

    fun names(): List<String> = all.map { it.name }

    fun entry(name: String): Entry? = byName[name]

    fun file(name: String): Path = dir.resolve("$name.json")

    /** Whether [name] can be loaded right now (downloaded earlier). */
    fun isAvailable(name: String): Boolean = name in byName && Files.isRegularFile(file(name))

    /** Loads list [name]. Throws [IllegalArgumentException] if unknown or not downloaded yet. */
    fun load(name: String): WordList {
        val e = requireNotNull(byName[name]) { "unknown language '$name'" }
        require(Files.isRegularFile(file(name))) { "language '$name' is not downloaded" }
        return parseList(name, Files.readString(file(name)), e.orderedByFrequency)
    }

    /** Parses a Monkeytype language file (extra keys are ignored). */
    fun parseList(name: String, text: String, orderedByFrequency: Boolean): WordList {
        val obj = JsonReader.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("not a language file")
        val words = (obj["words"] as? List<*>)?.filterIsInstance<String>()
            ?: throw IllegalArgumentException("no words in '$name'")
        require(words.isNotEmpty()) { "'$name' has no words" }
        return WordList(name, orderedByFrequency || obj["orderedByFrequency"] == true, words)
    }
}
