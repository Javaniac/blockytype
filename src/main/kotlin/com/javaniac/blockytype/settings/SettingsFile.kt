package com.javaniac.blockytype.settings

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val log = LoggerFactory.getLogger("SettingsFile")

/** One option of the settings file: its [key], the [comment] written above it, the [default] and what is [valid]. */
class SettingSpec(val key: String, val comment: String, val default: String, val valid: (String) -> Boolean)

/**
 * The server settings file (`blockytype.properties`): `key=value` lines, each with `#` comment lines above it.
 * Edits (by hand, or by the server for the in-game admin panel) only ever touch the value of one line, so
 * comments, unknown lines, blank lines and the order are kept. Missing options are added with their comment.
 */
class SettingsFile(val path: Path, private val specs: List<SettingSpec>) {
    private val byKey = specs.associateBy { it.key }

    /**
     * Reads the file, creating it or adding missing options first, and returns the valid values (invalid ones are
     * logged and left out, the file keeps them). [legacy] values (the old `server_settings` table) fill in options
     * the file does not have yet. The old `show-input` key becomes `input-box`.
     */
    @Synchronized
    fun load(legacy: Map<String, String> = emptyMap()): Map<String, String> {
        val original = if (Files.exists(path)) Files.readString(path) else ""
        var text = original
        val sep = lineSeparator(original)

        var present = parse(text)
        if ("input-box" !in present) {
            present["show-input"]?.let { old ->
                text = withValue(text, byKey.getValue("input-box"), if (old.equals("false", ignoreCase = true)) "hide" else "show", sep)
            }
        }
        present = parse(text)
        for (spec in specs) {
            if (spec.key in present) continue
            legacy[spec.key]?.trim()?.takeIf { spec.valid(it) }?.let { text = withValue(text, spec, it, sep) }
        }
        present = parse(text)
        for (spec in specs) if (spec.key !in present) text = withValue(text, spec, spec.default, sep)
        if (original.isBlank()) text = HEADER.lines().joinToString(sep) + sep + sep + text.trimStart('\r', '\n')

        if (text != original) write(text)

        val values = LinkedHashMap<String, String>()
        for ((key, value) in parse(text)) {
            val spec = byKey[key] ?: continue
            if (spec.valid(value)) values[key] = value else log.warn("Ignoring invalid value '$value' for '$key' in ${path.fileName}, using the default")
        }
        return values
    }

    /** Sets [key] to [value] in the file. Throws [IllegalArgumentException] for unknown keys and invalid values. */
    @Synchronized
    fun set(key: String, value: String) {
        val spec = requireNotNull(byKey[key]) { "unknown setting $key" }
        val v = value.trim()
        require(spec.valid(v)) { "invalid value '$value' for $key" }
        val original = if (Files.exists(path)) Files.readString(path) else ""
        write(withValue(original, spec, v, lineSeparator(original)))
    }

    /** Writes the default of [key] back. */
    fun reset(key: String) = set(key, requireNotNull(byKey[key]) { "unknown setting $key" }.default)

    private fun write(text: String) {
        path.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, text)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.io.IOException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun lineSeparator(text: String) = if ("\r\n" in text) "\r\n" else "\n"

    private fun keyOf(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return null
        val eq = t.indexOf('=')
        return if (eq <= 0) null else t.substring(0, eq).trim()
    }

    /** `key=value` of every option line (the last one wins). */
    private fun parse(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val key = keyOf(line) ?: continue
            out[key] = line.substring(line.indexOf('=') + 1).trim()
        }
        return out
    }

    /** [text] with the line of [spec] set to [value], or the option (comment first) appended when there is none. */
    private fun withValue(text: String, spec: SettingSpec, value: String, sep: String): String {
        val lines = text.split(sep).toMutableList()
        val at = lines.indexOfLast { keyOf(it) == spec.key }
        if (at >= 0) {
            lines[at] = "${spec.key}=$value"
            return lines.joinToString(sep)
        }
        val block = spec.comment.lines().joinToString(sep) { "# $it" } + sep + "${spec.key}=$value" + sep
        if (text.isBlank()) return block
        return text.trimEnd('\r', '\n') + sep + sep + block
    }

    companion object {
        const val HEADER = "# BlockyType settings\n" +
            "# One option per line as key=value. Lines starting with # are comments; the server keeps them when it rewrites this file.\n" +
            "# The in-game admin panel (Ctrl+Q) and the pack command change these values too."
    }
}
