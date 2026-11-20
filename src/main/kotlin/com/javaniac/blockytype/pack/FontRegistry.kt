package com.javaniac.blockytype.pack

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

private val log = LoggerFactory.getLogger("FontRegistry")

/**
 * Fonts the pack can use. Folder layout (in the working directory):
 * - `fonts/builtin/`: JetBrains Mono (from the jar) plus the built-in fonts of [FontManifest], downloaded by [FontInstaller],
 *   each with its license. Nothing else belongs here: foreign files are moved to `custom/` at startup.
 * - `fonts/custom/`: fonts the admin adds (`.ttf` / `.otf`, monospace only), optionally with `<name>.license.txt`.
 * - `fonts/README.txt`: explains this to the admin.
 * Every font is validated ([FontInfo.load]: monospace and not too thin) before it is listed or used.
 * Each player's pack carries one font (a resource pack has one), chosen by the font setting.
 */
object FontRegistry {
    /** The one font that is part of the jar, so the server always has a working font. */
    const val BUNDLED = "jetbrains-mono"

    private val EXTENSIONS = setOf("ttf", "otf")
    private const val LICENSE_SUFFIX = ".license.txt"

    /** The `fonts/` folder (tests use a temporary one). */
    @Volatile
    var root: Path = Path.of("fonts")

    /** The downloadable built-in fonts (tests use their own). */
    @Volatile
    var builtins: List<BuiltinFont> = FontManifest.ALL

    private val builtinDir get() = root.resolve("builtin")
    private val customDir get() = root.resolve("custom")

    /** What [init] did, for the console. */
    data class InitResult(val movedToCustom: List<String>)

    private class Checked(val stamp: Pair<Long, Long>, val info: FontInfo?, val error: String?)

    private val checked = ConcurrentHashMap<Path, Checked>()

    /**
     * Creates the folders and the README, writes JetBrains Mono (and its license) into `builtin/` and moves files that do
     * not belong there to `custom/` with a warning. Call once at startup.
     */
    fun init(): InitResult {
        Files.createDirectories(builtinDir)
        Files.createDirectories(customDir)
        val readme = root.resolve("README.txt")
        if (!Files.exists(readme)) Files.writeString(readme, README)
        copyIfMissing("/fonts/$BUNDLED.ttf", builtinDir.resolve("$BUNDLED.ttf"))
        copyIfMissing("/fonts/$BUNDLED$LICENSE_SUFFIX", builtinDir.resolve("$BUNDLED$LICENSE_SUFFIX"))

        val moved = mutableListOf<String>()
        Files.list(builtinDir).use { stream ->
            for (file in stream.filter { it.isRegularFile() }.toList()) {
                if (belongsInBuiltin(file.name)) continue
                var target = customDir.resolve(file.name)
                if (Files.exists(target)) target = customDir.resolve("moved-" + file.name)
                Files.move(file, target)
                moved += file.name
                log.warn("'${file.name}' does not belong in fonts/builtin (only the server's own fonts do): moved it to fonts/custom/${target.name}")
            }
        }
        names() // checks every font now, so invalid ones are reported at startup
        return InitResult(moved)
    }

    private fun belongsInBuiltin(file: String): Boolean {
        val known = builtins.map { it.name } + BUNDLED
        return known.any { n -> file == "$n.ttf" || file == "$n.otf" || file == "$n$LICENSE_SUFFIX" || file == "$n.ttf.part" || file == "$n$LICENSE_SUFFIX.part" }
    }

    /** Names of the built-in fonts of [builtins] that are on disk, whether valid or not. */
    private fun builtinNames() = builtins.map { it.name }

    /** Names of the fonts that can be used right now: the default first, then valid built-in fonts, then valid custom fonts. */
    fun names(): List<String> {
        val builtin = builtinNames().filter { n -> fileOf(builtinDir, n)?.let { check(it).info } != null }.sorted()
        val custom = if (Files.isDirectory(customDir)) {
            Files.list(customDir).use { s ->
                s.filter { it.isRegularFile() && it.extension.lowercase() in EXTENSIONS }.map { it.nameWithoutExtension }.toList()
            }.filter { it !in builtinNames() && it != BUNDLED }.filter { n -> fileOf(customDir, n)?.let { check(it).info } != null }.sorted()
        } else emptyList()
        return (listOf(BUNDLED) + builtin + custom).distinct()
    }

    private fun fileOf(dir: Path, name: String): Path? =
        EXTENSIONS.map { dir.resolve("$name.$it") }.firstOrNull { it.isRegularFile() }

    /** Validation of a font file, cached until its size or modification time changes. Logs a warning once per change. */
    private fun check(file: Path): Checked {
        val stamp = Files.getLastModifiedTime(file).toMillis() to Files.size(file)
        checked[file]?.takeIf { it.stamp == stamp }?.let { return it }
        val result = try {
            Checked(stamp, FontInfo.load(file.nameWithoutExtension, Files.readAllBytes(file), file.extension), null)
        } catch (e: IllegalArgumentException) {
            log.warn("Ignoring font ${root.relativize(file)}: ${e.message}")
            Checked(stamp, null, e.message)
        }
        checked[file] = result
        return result
    }

    /** Loads and validates font [name]. Throws [IllegalArgumentException] if missing, not monospace or too thin. */
    fun load(name: String): FontInfo {
        if (name == BUNDLED) return FontInfo.load(BUNDLED, resource("/fonts/$BUNDLED.ttf")!!, "ttf")
        val file = (if (name in builtinNames()) fileOf(builtinDir, name) else null) ?: fileOf(customDir, name)
            ?: throw IllegalArgumentException("unknown font '$name'")
        val c = check(file)
        return c.info ?: throw IllegalArgumentException(c.error ?: "unusable font")
    }

    /** Font [name], or the bundled one (with an error in the log) if it is missing or unusable. */
    fun loadOrBundled(name: String): FontInfo = try {
        load(name)
    } catch (e: Exception) {
        log.error("Font '$name' is unusable (${e.message}), using $BUNDLED")
        load(BUNDLED)
    }

    /** The license text of font [name], or null when there is none (a custom font without `<name>.license.txt`). */
    fun licenseOf(name: String): ByteArray? {
        if (name == BUNDLED) return resource("/fonts/$BUNDLED$LICENSE_SUFFIX")
        val dir = if (name in builtinNames()) builtinDir else customDir
        val file = dir.resolve("$name$LICENSE_SUFFIX")
        return if (file.isRegularFile()) Files.readAllBytes(file) else null
    }

    /** Validates [bytes] as a font and stores it as `fonts/custom/<name>.<extension>`. */
    fun save(name: String, bytes: ByteArray, extension: String): FontInfo {
        require(name != BUNDLED && name !in builtinNames()) { "'$name' is a built-in font name" }
        require(extension.lowercase() in EXTENSIONS) { "only .ttf and .otf fonts are supported" }
        val info = FontInfo.load(name, bytes, extension) // throws if invalid
        Files.createDirectories(customDir)
        EXTENSIONS.forEach { Files.deleteIfExists(customDir.resolve("$name.$it")) }
        Files.write(customDir.resolve("$name.${extension.lowercase()}"), bytes)
        return info
    }

    /** Where [FontInstaller] puts the built-in fonts. */
    fun builtinFolder(): Path = builtinDir

    private fun copyIfMissing(resource: String, target: Path) {
        if (Files.exists(target)) return
        Files.write(target, resource(resource) ?: return)
    }

    private fun resource(path: String): ByteArray? = FontRegistry::class.java.getResourceAsStream(path)?.use { it.readBytes() }

    private const val README = """BlockyType fonts
================

builtin/  The server's own fonts. JetBrains Mono ships with the server, the others are downloaded on
          first start (with their licenses). Do not put your own fonts here: anything the server does not
          know is moved to custom/ with a warning.

custom/   Put your own fonts here as .ttf or .otf files. Optionally add <name>.license.txt next to a font
          to ship its license with the resource pack.

Custom fonts can only be MONOSPACE fonts (every character equally wide), because the game screen is a fixed
grid, and they need a Regular weight (not Thin or Light). The server checks every font before it uses it;
fonts that fail the check are ignored and a warning with the reason is logged.

Pick the font in the admin panel (Ctrl+Q), with 'pack font use <name>', or with the font option in
blockytype.properties.
"""
}
