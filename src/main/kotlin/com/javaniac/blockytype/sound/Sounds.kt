package com.javaniac.blockytype.sound

import java.nio.file.Files
import java.nio.file.Path
import com.javaniac.blockytype.pack.Json

/**
 * The sounds of the sound settings. The files are Monkeytype's click, error and time warning sounds (GPL-3.0 repo,
 * commit `78a22fd…`, see `SOURCE.txt` in the sounds folder), converted to Ogg Vorbis, plus notes synthesized for the
 * sets Monkeytype generates in code. `index.txt` lists them; they are downloaded on the first start ([SoundDownloader]) and the resource pack gets them as `blockytype:` sounds.
 */
object Sounds {
    const val NAMESPACE = "blockytype"

    /** Folder of a sound inside the pack, below `assets/blockytype/`. */
    const val PACK_DIR = "assets/$NAMESPACE/sounds/"

    const val TIME_WARNING = "$NAMESPACE:time_warning"

    /** The click sets of the `play-sound-on-click` setting, option to Monkeytype set number. */
    private val CLICK_SETS = linkedMapOf(
        "click" to 1, "beep" to 2, "pop" to 3, "nk creams" to 4, "typewriter" to 5, "osu" to 6, "hitmarker" to 7,
        "sine" to 8, "sawtooth" to 9, "square" to 10, "triangle" to 11, "pentatonic" to 12, "wholetone" to 13,
        "fist fight" to 14, "rubber keys" to 15, "fart" to 16, "akko lavenders" to 17, "cherrymx black abs" to 18,
        "cherrymx black pbt" to 19, "cherrymx blue abs" to 20, "cherrymx blue pbt" to 21, "cherrymx brown pbt" to 22,
        "kalih box white" to 23, "razer green" to 24, "tealios v2" to 25, "trust gxt" to 26,
    )

    /** Sets that play one fixed note per key instead of a random sample. */
    private val NOTE_SETS = 8..11

    /** Notes of an oscillator set. */
    const val NOTES = 14

    private val ERRORS = mapOf("damage" to 1, "triangle" to 2, "square" to 3, "missed punch" to 4)

    /** Where [SoundDownloader] stores the sounds (tests point `blockytype.sounds.dir` at `assets/sounds`). */
    @Volatile
    var dir: Path = Path.of(System.getProperty("blockytype.sounds.dir") ?: "sounds")

    /** Every sound file, relative to the sounds folder (`click1/1.ogg`). Empty until the sounds are downloaded. */
    fun files(): List<String> {
        val index = dir.resolve("index.txt")
        if (!Files.isRegularFile(index)) return emptyList()
        return Files.readAllLines(index).map { it.trim() }.filter { it.endsWith(".ogg") }
    }

    fun bytes(file: String): ByteArray {
        val path = dir.resolve(file).normalize()
        require(path.startsWith(dir.normalize())) { "not a sound file: $file" }
        return Files.readAllBytes(path)
    }

    /** The sound event to play for [char] with the click setting [option], or null (off or unknown). */
    fun clickEvent(option: String, char: Char): String? {
        val set = CLICK_SETS[option] ?: return null
        if (set in NOTE_SETS) return "$NAMESPACE:click${set}_${note(char)}"
        return "$NAMESPACE:click$set"
    }

    /** Which of the [NOTES] notes a key plays: letters go up the scale, other keys by their code. */
    fun note(char: Char): Int {
        val c = char.lowercaseChar()
        return (if (c in 'a'..'z') c - 'a' else c.code) % NOTES
    }

    fun errorEvent(option: String): String? = ERRORS[option]?.let { "$NAMESPACE:error$it" }

    /** `play-sound-on-error`'s `1 second` style option as seconds, or null for off. */
    fun warningSeconds(option: String): Int? = option.substringBefore(' ').toIntOrNull()

    /** `sound-volume` as a volume 0..1; the default for anything unreadable. */
    fun volume(option: String): Float = option.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f

    /**
     * The pack's `sounds.json`: one event per sample set (the client picks a variant at random), one per note of the
     * oscillator sets, one per error sound and the time warning.
     */
    fun soundsJson(): String {
        val byFolder = files().map { it.removeSuffix(".ogg") }.groupBy { it.substringBeforeLast('/', "") }
        val events = linkedMapOf<String, Any?>()
        fun event(name: String, sounds: List<String>) {
            events[name] = linkedMapOf("sounds" to sounds.map { "$NAMESPACE:$it" })
        }
        for ((folder, list) in byFolder.toSortedMap(compareBy({ it.length }, { it }))) {
            if (folder.isEmpty()) continue
            val set = folder.removePrefix("click").toIntOrNull()
            if (folder.startsWith("click") && set in NOTE_SETS) {
                list.forEach { event("${folder}_${it.substringAfterLast('/')}", listOf(it)) }
            } else {
                event(folder, list.sorted())
            }
        }
        if ("time_warning" in files().map { it.removeSuffix(".ogg") }) event("time_warning", listOf("time_warning"))
        return Json.write(events)
    }
}

/**
 * Plays the time warning once: [check] with the seconds left, true the first time they are at or below the
 * wanted [seconds]. A null [seconds] is off. [reset] arms it again (new test).
 */
class TimeWarning(private val seconds: Int?) {
    private var fired = false

    fun check(remaining: Int): Boolean {
        if (seconds == null || fired || remaining <= 0 || remaining > seconds) return false
        fired = true
        return true
    }

    fun reset() {
        fired = false
    }
}
