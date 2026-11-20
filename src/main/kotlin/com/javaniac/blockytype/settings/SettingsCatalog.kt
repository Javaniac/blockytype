package com.javaniac.blockytype.settings

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.pack.FontRegistry
import com.javaniac.blockytype.words.LanguageRegistry

/** Sections of the settings page, in Monkeytype's order. */
enum class Section(val label: String) {
    BEHAVIOR("behavior"),
    INPUT("input"),
    SOUND("sound"),
    CARET("caret"),
    APPEARANCE("appearance"),
    THEME("theme"),
    HIDE_ELEMENTS("hide elements"),
    DANGER_ZONE("danger zone"),
}

/** The choices of a setting, shown as a row of buttons. */
sealed interface Options {
    val isFixed: Boolean

    /** Too many values for one row of buttons: the menu shows the current one and a pager instead. */
    val paged: Boolean get() = false

    fun values(): List<String>

    class Fixed(private val list: List<String>) : Options {
        override val isFixed get() = true

        override fun values() = list
    }

    /** A long list ([paged]) such as the languages. */
    class Paged(private val provider: () -> List<String>) : Options {
        override val isFixed get() = false

        override val paged get() = true

        override fun values() = provider()
    }

    /** Depends on the server (installed themes and fonts), so it is read when needed. */
    class Dynamic(private val provider: () -> List<String>) : Options {
        override val isFixed get() = false

        override fun values() = provider()
    }
}

/**
 * One setting. [implemented] settings change the game; the rest are Monkeytype settings that are shown and
 * stored but do nothing yet. [serverDefault] settings have a default that admins edit in the admin panel;
 * a player's own choice always wins over it.
 */
data class Setting(
    val id: String,
    val section: Section,
    val title: String,
    val description: String,
    val options: Options,
    val default: String,
    val implemented: Boolean = false,
    val serverDefault: Boolean = false,
)

object SettingsCatalog {
    private fun fixed(vararg values: String) = Options.Fixed(values.toList())

    private val onOff = fixed("off", "on")

    private fun inert(
        id: String, section: Section, title: String, description: String, options: Options, default: String,
    ) = Setting(id, section, title, description, options, default)

    private fun impl(
        id: String, section: Section, title: String, description: String, options: Options, default: String,
    ) = Setting(id, section, title, description, options, default, implemented = true)

    val all: List<Setting> = listOf(
        // behavior
        impl("difficulty", Section.BEHAVIOR, "difficulty",
            "Normal is the classic typing test experience. Expert fails the test if you submit (press space) an incorrect word. Master fails if you press a single incorrect key (meaning you have to achieve 100% accuracy).",
            fixed("normal", "expert", "master"), "normal"),
        impl("quick-restart", Section.BEHAVIOR, "quick restart",
            "Quickly restart the test with a key. In BlockyType F restarts, Q opens the settings and /settings changes them in chat.",
            fixed("off", "enter"), "off"),
        impl("keybind-check", Section.BEHAVIOR, "keybind check",
            "How often the game asks your client for its key names, so the key hints follow your keybinds. A check opens a hidden anvil for a moment, so players with a high ping may want it rarer or off.",
            fixed("off", "1 min", "2 min", "5 min", "10 min", "30 min"), "5 min"),
        impl("test-mode", Section.BEHAVIOR, "test mode",
            "Words: type a number of words. Time: type as many words as you can before the time runs out.",
            fixed("words", "time"), "words"),
        impl("word-count", Section.BEHAVIOR, "words",
            "Number of words of a words test.", fixed("10", "25", "50", "100"), "25"),
        impl("time-limit", Section.BEHAVIOR, "time",
            "Seconds of a time test.", fixed("15", "30", "60", "120"), "30"),
        impl("language", Section.BEHAVIOR, "language",
            "Change in which language you want to type. Lists marked with * are downloaded when you pick them.",
            Options.Paged { LanguageRegistry.names() }, LanguageRegistry.DEFAULT),
        impl("blind-mode", Section.BEHAVIOR, "blind mode",
            "No errors or incorrect words are highlighted. Helps you to focus on raw speed.", onOff, "off"),
        impl("min-speed", Section.BEHAVIOR, "min speed",
            "Automatically fails a test if your speed (wpm) falls below a threshold.",
            fixed("off", "20", "40", "60", "80", "100"), "off"),
        impl("min-accuracy", Section.BEHAVIOR, "min accuracy",
            "Automatically fails a test if your accuracy (%) falls below a threshold.",
            fixed("off", "75", "90", "95", "100"), "off"),
        impl("min-word-burst", Section.BEHAVIOR, "min word burst",
            "Automatically fails a test if your raw for a single word falls below 30 wpm. 'flex' lets the threshold decrease for longer words.",
            fixed("off", "fixed", "flex"), "off"),
        impl("british-english", Section.BEHAVIOR, "british english",
            "Use the British spelling instead of American.", onOff, "off"),
        impl("funbox", Section.BEHAVIOR, "funbox",
            "Special modes that change the word generation, the behavior or the looks.",
            fixed("off", "rAnDoMcAsE", "sPoNgEcAsE", "capitals", "gibberish", "ascii", "specials", "memory", "nospace",
                "pseudolang", "IPv4", "IPv6", "binary", "hexadecimal", "zipf", "morse", "backwards", "ddoouubblleedd",
                "instant messaging", "underscore_spaces", "ALL CAPS", "polyglot", "rot13"),
            "off"),

        // input
        impl("freedom-mode", Section.INPUT, "freedom mode", "Allows you to delete any word, even if it was typed correctly.", onOff, "off"),
        impl("strict-space", Section.INPUT, "strict space",
            "Pressing space at the beginning of a word will insert a space character when this mode is enabled.", onOff, "off"),
        impl("stop-on-error", Section.INPUT, "stop on error",
            "Letter mode will stop input when pressing any incorrect letters. Word mode will not allow you to continue to the next word until you correct all mistakes.",
            fixed("off", "word", "letter"), "off"),
        impl("delete-on-error", Section.INPUT, "delete on error",
            "Letter mode will automatically delete the incorrect character and the one before it. Word mode will delete the entire current word. The hard variants will additionally jump back to the previous word if you make a mistake on the first character.",
            fixed("off", "letter", "letter hard", "word", "word hard"), "off"),
        impl("confidence-mode", Section.INPUT, "confidence mode",
            "When enabled, you will not be able to go back to previous words to fix mistakes. When turned up to the max, you won't be able to backspace at all.",
            fixed("off", "on", "max"), "off"),
        impl("quick-end", Section.INPUT, "quick end",
            "Words mode only: the test ends as soon as the last word has been typed, even if it's incorrect.", onOff, "on"),
        impl("indicate-typos", Section.INPUT, "indicate typos",
            "Shows typos that you've made: 'replace' replaces the letters with the ones you typed.",
            fixed("off", "replace"), "off"),
        impl("hide-extra-letters", Section.INPUT, "hide extra letters",
            "Hides extra letters. This avoids words jumping lines, but might feel confusing when you press a key and nothing happens.", onOff, "off"),
        impl("lazy-mode", Section.INPUT, "lazy mode",
            "Replaces accents, diacritics and special characters with their normal letter equivalents.", onOff, "off"),

        // sound
        impl("sound-volume", Section.SOUND, "sound volume", "Change the volume of the sound effects.",
            fixed("0", "0.25", "0.5", "0.75", "1"), "0.5"),
        impl("play-sound-on-click", Section.SOUND, "play sound on click", "Plays a short sound when you press a key.",
            fixed("off", "click", "beep", "pop", "nk creams", "typewriter", "osu", "hitmarker", "sine", "sawtooth", "square",
                "triangle", "pentatonic", "wholetone", "fist fight", "rubber keys", "fart", "akko lavenders",
                "cherrymx black abs", "cherrymx black pbt", "cherrymx blue abs", "cherrymx blue pbt",
                "cherrymx brown pbt", "kalih box white", "razer green", "tealios v2", "trust gxt"),
            "off"),
        impl("play-sound-on-error", Section.SOUND, "play sound on error",
            "Plays a short sound if you press an incorrect key or press space too early.",
            fixed("off", "damage", "triangle", "square", "missed punch"), "off"),
        impl("play-time-warning", Section.SOUND, "play time warning",
            "Play a short warning sound if you are close to the end of a timed test.",
            fixed("off", "1 second", "3 seconds", "5 seconds", "10 seconds"), "off"),

        // caret
        impl("caret-style", Section.CARET, "caret style", "Change the style of the caret during the test.",
            fixed("off", "default", "block", "outline", "underline"), "default"),
        impl("pace-caret", Section.CARET, "pace caret",
            "Displays a second caret that moves at constant speed. 'average' averages the speed of the last 10 results, 'pb' uses your personal best.",
            fixed("off", "average", "pb", "last", "custom"), "off"),
        impl("pace-caret-custom", Section.CARET, "pace caret custom speed",
            "Speed (wpm) of the pace caret when pace caret is set to custom.",
            fixed("40", "60", "80", "100", "120"), "60"),
        impl("pace-caret-style", Section.CARET, "pace caret style", "Change the style of the pace caret during the test.",
            fixed("off", "default", "block", "outline", "underline"), "default"),

        // appearance
        Setting("animation", Section.APPEARANCE, "animation",
            "Slide the caret smoothly to the next letter. Admins pick the server default, you can override it.",
            fixed("on", "off"), "on", implemented = true, serverDefault = true),
        Setting("anti-aliasing", Section.APPEARANCE, "anti-aliasing",
            "Smooth the edges of the text. Turn it off for crisp pixels, or if text looks blurry on your GUI scale. Reloads the resource pack.",
            fixed("on", "off"), "on", implemented = true, serverDefault = true),
        Setting("input-box", Section.APPEARANCE, "input box",
            "Show the box where you type, so you can see and fix what you typed. Reloads the resource pack.",
            fixed("show", "hide"), "show", implemented = true, serverDefault = true),
        Setting("font", Section.APPEARANCE, "font family",
            "Monospace font of the typing test. Reloads the resource pack.",
            Options.Dynamic { FontRegistry.names() }, "jetbrains-mono", implemented = true, serverDefault = true),
        impl("live-progress-style", Section.APPEARANCE, "live progress style",
            "Change the style of the timer/word count during a test.",
            fixed("off", "bar", "text", "mini"), "mini"),
        impl("live-speed-style", Section.APPEARANCE, "live speed style",
            "Change the style of the live speed displayed during the test.", fixed("off", "text", "mini"), "mini"),
        impl("live-accuracy-style", Section.APPEARANCE, "live accuracy style",
            "Change the style of the live accuracy displayed during the test.", fixed("off", "text", "mini"), "off"),
        impl("live-word-burst-style", Section.APPEARANCE, "live word burst style",
            "Change the style of the live burst speed displayed during the test.", fixed("off", "text", "mini"), "off"),
        impl("live-progress-color", Section.APPEARANCE, "live progress color",
            "Change the color of the progress, live speed, accuracy and burst text.", fixed("black", "sub", "text", "main"), "main"),
        impl("live-progress-opacity", Section.APPEARANCE, "live progress opacity",
            "Change the opacity of the progress, live speed, burst and accuracy text.", fixed("0.25", "0.5", "0.75", "1"), "0.5"),
        impl("highlight-mode", Section.APPEARANCE, "highlight mode", "Change what is highlighted during the test.",
            fixed("off", "letter", "word", "next word", "next two words", "next three words"), "off"),
        impl("typed-effect", Section.APPEARANCE, "typed effect", "Change how typed words are shown.",
            fixed("keep", "hide", "fade", "dots"), "keep"),
        impl("tape-mode", Section.APPEARANCE, "tape mode",
            "Only shows one line which scrolls horizontally. 'word' scrolls after every word and 'letter' after every keypress.",
            fixed("off", "letter", "word"), "off"),
        impl("tape-margin", Section.APPEARANCE, "tape margin",
            "In tape mode, the caret position from the left edge as a percentage.", fixed("10", "25", "50"), "50"),
        impl("always-show-decimal-places", Section.APPEARANCE, "always show decimal places",
            "Always shows decimal places for values on the result page.", onOff, "off"),
        impl("typing-speed-unit", Section.APPEARANCE, "typing speed unit", "Display typing speed in the specified unit.",
            fixed("wpm", "cpm", "wps", "cps", "wph"), "wpm"),
        impl("start-graphs-at-zero", Section.APPEARANCE, "start graphs at zero",
            "Force graph axis to always start at zero, no matter what the data is.", onOff, "on"),
        impl("max-line-width", Section.APPEARANCE, "max line width",
            "Maximum width of the typing test, measured in characters.", fixed("30", "40", "50"), "50"),
        impl("font-size", Section.APPEARANCE, "font size", "Change the font size of the test words.",
            fixed("1", "1.5", "2"), "1"),

        // theme
        Setting("theme", Section.THEME, "theme",
            "Completely change the look and feel by picking one of the presets.",
            Options.Dynamic { BlockyType.themes.map { it.name.lowercase() } }, "default", implemented = true, serverDefault = true),
        impl("flip-test-colors", Section.THEME, "flip test colors",
            "By default, typed text is brighter than the future text. When enabled, the colors are flipped.", onOff, "off"),
        impl("colorful-mode", Section.THEME, "colorful mode",
            "When off, mistakes are not colored: they use the text color. Use indicate typos to still see what you typed.", onOff, "on"),
        impl("random-theme", Section.THEME, "random theme",
            "When you join, the theme is set to a random one (any, only light or only dark ones). Reloads the resource pack.",
            fixed("off", "on", "light", "dark"), "off"),

        // hide elements
        impl("show-key-tips", Section.HIDE_ELEMENTS, "show key tips", "Shows the keybind tips at the bottom of the page.", fixed("hide", "show"), "show"),
        impl("chat-summary", Section.HIDE_ELEMENTS, "chat summary",
            "Chat message after a test: short = one line with wpm, accuracy, raw and consistency; full adds mode, time, personal best and average.",
            fixed("off", "short", "full"), "full"),
        impl("show-average", Section.HIDE_ELEMENTS, "show average",
            "Displays your average speed and/or accuracy over the last 10 tests.", fixed("off", "speed", "acc", "both"), "off"),

        // danger zone
        impl("reset-settings", Section.DANGER_ZONE, "reset settings",
            "Resets all your settings to the server defaults. You can't undo this!", fixed("reset settings"), "reset settings"),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Setting? = byId[id]

    fun isValid(id: String, value: String): Boolean = byId[id]?.options?.values()?.contains(value) == true

    /** [user] overrides, then [server] defaults, then the catalog default. Invalid stored values are skipped. */
    fun effective(id: String, user: Map<String, String>, server: Map<String, String>): String {
        val setting = byId.getValue(id)
        user[id]?.takeIf { isValid(id, it) }?.let { return it }
        if (setting.serverDefault) server[id]?.takeIf { isValid(id, it) }?.let { return it }
        return setting.default
    }
}
