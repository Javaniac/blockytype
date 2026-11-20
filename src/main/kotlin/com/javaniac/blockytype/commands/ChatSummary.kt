package com.javaniac.blockytype.commands

import com.javaniac.blockytype.game.SpeedUnit
import com.javaniac.blockytype.game.TestResult
import com.javaniac.blockytype.game.TestRules
import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The chat output of the game: one short summary after a finished test, one line when a test fails, and the format
 * of player chat. Pure (returns components), colored with the player's theme.
 *
 * ```
 * » 112 wpm  97% acc  raw 118  74% cons
 *   words 25 english  ·  30.2s  ·  pb 120 (-8)  ·  avg10 104
 * ```
 */
object ChatSummary {
    private const val SEP = "  ·  "

    private fun prefix(t: AbstractTheme) = Component.text("» ", t.caretColor)

    private fun text(s: String, color: TextColor) = Component.text(s, color)

    /**
     * The summary of [r]. [level] is the `chat-summary` setting: `off` (nothing), `short` (one line), `full` (two lines).
     * [previousBest] is the player's best wpm in this mode before this test, [average] the average of their last tests.
     */
    fun build(
        r: TestResult, mode: String, language: String, unit: SpeedUnit, decimals: Boolean, level: String,
        previousBest: Double?, average: Double?, t: AbstractTheme,
    ): List<Component> {
        if (level != "short" && level != "full") return emptyList()
        fun speed(wpm: Double) = unit.format(wpm, decimals)
        fun percent(v: Double) = if (decimals) "%.2f%%".format(Locale.ROOT, v) else "${v.roundToInt()}%"
        val newBest = previousBest == null || r.wpm > previousBest

        val hover = Component.text()
            .append(text("characters ", t.subColor))
            .append(text("${r.counts.correct}/${r.counts.incorrect}/${r.counts.extra}/${r.counts.missed}", t.textColor))
            .append(Component.newline())
            .append(text("errors ", t.subColor)).append(text("${r.perSecond.sumOf { it.errors }}", t.textColor))
            .append(Component.newline())
            .append(text("peak raw ", t.subColor)).append(text(speed(r.perSecond.maxOf { it.raw }), t.textColor))
            .append(Component.newline())
            .append(text("click to restart", t.subAltColor))
            .build()

        val head = Component.text()
            .append(prefix(t))
            .append(text(speed(r.wpm), t.mainColor).decorate(TextDecoration.BOLD)).append(text(" ${unit.id}", t.subColor))
            .append(text("  ", t.subColor)).append(text(percent(r.accuracy), t.textColor)).append(text(" acc", t.subColor))
            .append(text("  ", t.subColor)).append(text("raw ", t.subColor)).append(text(speed(r.raw), t.textColor))
            .append(text("  ", t.subColor)).append(text(percent(r.consistency), t.textColor)).append(text(" cons", t.subColor))
        if (level == "short" && newBest) head.append(text(" ★", t.mainColor))
        val headLine = head.build()
            .hoverEvent(HoverEvent.showText(hover))
            .clickEvent(ClickEvent.runCommand("/restart"))
        if (level == "short") return listOf(headLine)

        val second = Component.text()
            .append(text("  ", t.subColor))
            .append(text(mode, t.textColor)).append(text(" $language", t.subColor))
            .append(text(SEP, t.subAltColor)).append(text("%.1fs".format(Locale.ROOT, r.seconds), t.textColor))
            .append(text(SEP, t.subAltColor))
        if (newBest) {
            second.append(text("new pb ★", t.mainColor))
            if (previousBest != null) second.append(text(" (+${speed(r.wpm - previousBest)})", t.mainColor))
        } else {
            second.append(text("pb ", t.subColor)).append(text(speed(previousBest!!), t.textColor))
                .append(text(" (-${speed(previousBest - r.wpm)})", t.subColor))
        }
        if (average != null) {
            second.append(text(SEP, t.subAltColor)).append(text("avg10 ", t.subColor)).append(text(speed(average), t.textColor))
        }
        return listOf(headLine, second.build())
    }

    /** One line saying why a test failed. */
    fun failed(reason: String, rules: TestRules, t: AbstractTheme): Component {
        val why = when (reason) {
            "expert" -> "expert, a wrong word was submitted"
            "master" -> "master, a key was wrong"
            "min speed" -> "speed fell below ${rules.minSpeed} wpm"
            "min accuracy" -> "accuracy fell below ${rules.minAccuracy}%"
            "min word burst" -> "a word was slower than ${TestRules.BURST_WPM.toInt()} wpm"
            else -> reason
        }
        return Component.text().append(prefix(t)).append(text("failed: ", t.errorColor)).append(text(why, t.textColor)).build()
    }

    /** A chat message of a player: `[Server Admin] [best wpm] Name » message` (the tag for admins only), `-` while they have no result. */
    fun playerChat(
        t: AbstractTheme, name: String, bestWpm: Double?, unit: SpeedUnit, message: Component, admin: Boolean = false,
    ): Component {
        val best = bestWpm?.let { unit.format(it) } ?: "-"
        val out = Component.text()
        if (admin) out.append(AdminTag.tag(t)).append(text(" ", t.subColor))
        return out
            .append(text("[", t.subAltColor)).append(text(best, if (bestWpm == null) t.subColor else t.mainColor))
            .append(text("] ", t.subAltColor))
            .append(text(name, t.textColor)).append(text(" » ", t.caretColor))
            .append(message.colorIfAbsent(t.textColor))
            .build()
    }
}
