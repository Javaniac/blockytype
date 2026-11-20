package com.javaniac.blockytype.game

import com.javaniac.blockytype.ui.CaretStyle
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.TextScale
import com.javaniac.blockytype.ui.UiFont
import kotlin.math.roundToInt

/** Typing speed unit of the HUD, the result and the chat (`typing-speed-unit`). Values are computed in wpm. */
enum class SpeedUnit(val id: String, private val perWpm: Double, private val alwaysDecimals: Boolean) {
    WPM("wpm", 1.0, false),
    CPM("cpm", 5.0, false),
    WPS("wps", 5.0 / 60.0 / 5.0, true), // words per second
    CPS("cps", 5.0 / 60.0, true), // characters per second
    WPH("wph", 60.0, false);

    /** [wpm] in this unit. */
    fun convert(wpm: Double): Double = wpm * perWpm

    /** [wpm] as text in this unit; with [decimals] (or a unit that needs them) two decimal places. */
    fun format(wpm: Double, decimals: Boolean = false): String {
        val v = convert(wpm)
        return if (decimals || alwaysDecimals) "%.2f".format(java.util.Locale.ROOT, v) else "${v.roundToInt()}"
    }

    companion object {
        fun of(id: String): SpeedUnit = entries.firstOrNull { it.id == id } ?: WPM
    }
}

/** How a live value is shown: not at all, with its label, as a bare number, or (progress only) as a bar. */
enum class LiveStyle {
    OFF, TEXT, MINI, BAR;

    companion object {
        fun of(id: String): LiveStyle = when (id) {
            "text" -> TEXT
            "mini" -> MINI
            "bar" -> BAR
            else -> OFF
        }
    }
}

/**
 * What the player chose for how a test looks (appearance, theme, input and hide-elements settings). The defaults
 * are the catalog defaults, so a view built without settings looks like the plain test.
 */
data class ViewOptions(
    /** No mistakes are highlighted. */
    val blind: Boolean = false,
    val hideExtra: Boolean = false,
    /** Wrong letters show what was typed instead of the target letter. */
    val showTypos: Boolean = false,
    val highlight: Highlight = Highlight.OFF,
    val effect: TypedEffect = TypedEffect.KEEP,
    val tape: Tape = Tape.OFF,
    /** Caret position in tape mode, percent of the line width. */
    val tapeMargin: Int = 50,
    val flip: Boolean = false,
    val colorful: Boolean = true,
    /** Funbox `memory`: the words disappear once the test starts. */
    val memory: Boolean = false,
    val progress: LiveStyle = LiveStyle.MINI,
    val speed: LiveStyle = LiveStyle.MINI,
    val accuracy: LiveStyle = LiveStyle.OFF,
    val burst: LiveStyle = LiveStyle.OFF,
    val liveColor: Role = Role.MAIN,
    val liveOpacity: Double = 0.5,
    val unit: SpeedUnit = SpeedUnit.WPM,
    val decimals: Boolean = false,
    val startGraphsAtZero: Boolean = true,
    val keyTips: Boolean = true,
    val caret: CaretStyle = CaretStyle.LINE,
    val scale: TextScale = TextScale.X1,
    val paceStyle: CaretStyle = CaretStyle.LINE,
    /** `pace-caret`: off, average, pb, last or custom. */
    val paceMode: String = "off",
    val paceCustom: Int = 60,
    /** `show-average`: what of the last 10 tests the config bar shows. */
    val average: AverageShown = AverageShown.OFF,
    /** Chars per line of the target text (`max-line-width`). */
    val width: Int = UiFont.MAX_LINE_CHARS,
) {
    enum class Highlight { OFF, LETTER, WORD, NEXT_WORD, NEXT_TWO, NEXT_THREE }

    enum class TypedEffect { KEEP, HIDE, FADE, DOTS }

    enum class Tape { OFF, LETTER, WORD }

    enum class AverageShown { OFF, SPEED, ACC, BOTH }

    companion object {
        fun from(effective: (String) -> String): ViewOptions = ViewOptions(
            blind = effective("blind-mode") == "on",
            hideExtra = effective("hide-extra-letters") == "on",
            showTypos = effective("indicate-typos") == "replace",
            highlight = when (effective("highlight-mode")) {
                "letter" -> Highlight.LETTER
                "word" -> Highlight.WORD
                "next word" -> Highlight.NEXT_WORD
                "next two words" -> Highlight.NEXT_TWO
                "next three words" -> Highlight.NEXT_THREE
                else -> Highlight.OFF
            },
            effect = when (effective("typed-effect")) {
                "hide" -> TypedEffect.HIDE
                "fade" -> TypedEffect.FADE
                "dots" -> TypedEffect.DOTS
                else -> TypedEffect.KEEP
            },
            tape = when (effective("tape-mode")) {
                "letter" -> Tape.LETTER
                "word" -> Tape.WORD
                else -> Tape.OFF
            },
            tapeMargin = effective("tape-margin").toIntOrNull()?.coerceIn(0, 100) ?: 50,
            flip = effective("flip-test-colors") == "on",
            colorful = effective("colorful-mode") != "off",
            memory = effective("funbox") == "memory",
            progress = LiveStyle.of(effective("live-progress-style")),
            speed = LiveStyle.of(effective("live-speed-style")),
            accuracy = LiveStyle.of(effective("live-accuracy-style")),
            burst = LiveStyle.of(effective("live-word-burst-style")),
            liveColor = when (effective("live-progress-color")) {
                "black" -> Role.BG
                "sub" -> Role.SUB
                "text" -> Role.TEXT
                else -> Role.MAIN
            },
            liveOpacity = effective("live-progress-opacity").toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
            unit = SpeedUnit.of(effective("typing-speed-unit")),
            decimals = effective("always-show-decimal-places") == "on",
            startGraphsAtZero = effective("start-graphs-at-zero") != "off",
            keyTips = effective("show-key-tips") != "hide",
            caret = CaretStyle.of(effective("caret-style")),
            paceStyle = CaretStyle.of(effective("pace-caret-style")),
            paceMode = effective("pace-caret"),
            paceCustom = effective("pace-caret-custom").toIntOrNull() ?: 60,
            average = when (effective("show-average")) {
                "speed" -> AverageShown.SPEED
                "acc" -> AverageShown.ACC
                "both" -> AverageShown.BOTH
                else -> AverageShown.OFF
            },
            width = TextScale.of(effective("font-size")).let { sc -> (effective("max-line-width").toIntOrNull() ?: UiFont.MAX_LINE_CHARS).coerceIn(10, sc.maxChars) },
            scale = TextScale.of(effective("font-size")),
        )
    }
}
