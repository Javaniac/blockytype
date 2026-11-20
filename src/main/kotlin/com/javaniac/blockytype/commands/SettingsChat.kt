package com.javaniac.blockytype.commands

import com.javaniac.blockytype.menu.MenuHost
import com.javaniac.blockytype.settings.Section
import com.javaniac.blockytype.settings.Setting
import com.javaniac.blockytype.settings.SettingsCatalog
import com.javaniac.blockytype.themes.AbstractTheme
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextDecoration

/**
 * The logic of `/settings <setting> [value]`: reads and changes a player's own settings through a [MenuHost],
 * the same way the settings page does. Pure (returns the chat messages), so it is unit-tested. Messages are
 * colored with the player's theme: [theme] is read after the change, so switching the theme colors its own reply.
 */
object SettingsChat {
    /** Setting ids that hold a value (the danger zone's reset button is the `reset` argument instead). */
    private const val MAX_LISTED = 30

    fun ids(): List<String> = SettingsCatalog.all.map { it.id }.filter { it != "reset-settings" }

    fun run(host: MenuHost, args: List<String>, theme: () -> AbstractTheme): List<Component> {
        if (args.isEmpty()) return listOf(list(theme()))
        if (args[0].equals("reset", ignoreCase = true)) {
            host.resetUserSettings()
            return listOf(line(theme(), Component.text("All your settings were reset", theme().textColor)))
        }
        val setting = ids().firstOrNull { it.equals(args[0], ignoreCase = true) }?.let { SettingsCatalog.byId(it) }
        if (setting == null) {
            val t = theme()
            return listOf(
                line(
                    t,
                    Component.text("Unknown setting ", t.errorColor), Component.text(args[0], t.errorColor),
                    Component.newline(), indent(t), Component.text("/settings", t.mainColor),
                    Component.text(" lists them", t.subColor),
                ),
            )
        }
        val choices = host.choices(setting.id)
        if (args.size == 1) return listOf(show(host, setting, choices, theme()))
        val wanted = args.drop(1).joinToString(" ")
        if (wanted.equals("default", ignoreCase = true)) {
            if (!setting.serverDefault) {
                val t = theme()
                return listOf(line(t, Component.text(setting.title, t.textColor), Component.text(" has no server default", t.errorColor)))
            }
            host.clearUser(setting.id)
            val t = theme()
            return listOf(line(t, Component.text(setting.title, t.textColor), Component.text(" follows the server default again", t.subColor)))
        }
        val value = choices.firstOrNull { it.equals(wanted, ignoreCase = true) }
        if (value == null) {
            val t = theme()
            return listOf(
                line(
                    t,
                    Component.text("Invalid value ", t.errorColor), Component.text(wanted, t.errorColor),
                    Component.text(" for ", t.subColor), Component.text(setting.title, t.textColor),
                    Component.newline(), indent(t), choiceList(setting, choices, null, t),
                ),
            )
        }
        host.setUser(setting.id, value)
        val t = theme()
        return listOf(
            line(
                t,
                Component.text(setting.title, t.textColor), Component.text(" set to ", t.subColor),
                Component.text(value, t.mainColor).decorate(TextDecoration.BOLD),
            ),
        )
    }

    private fun show(host: MenuHost, setting: Setting, choices: List<String>, t: AbstractTheme): Component {
        val current = host.userValue(setting.id) ?: host.serverValue(setting.id) ?: setting.default
        return line(
            t,
            Component.text(setting.title, t.textColor), Component.text(": ", t.subColor),
            Component.text(current, t.mainColor).decorate(TextDecoration.BOLD),
            Component.newline(), indent(t), choiceList(setting, choices, current, t),
        )
    }

    /** The choices as clickable words: the current one in the main color, the others in the sub color. */
    private fun choiceList(setting: Setting, choices: List<String>, current: String?, t: AbstractTheme): Component {
        val b = Component.text()
        val shown = if (choices.size > MAX_LISTED) choices.take(MAX_LISTED) else choices
        val all = shown + if (setting.serverDefault) listOf("default") else emptyList()
        all.forEachIndexed { i, c ->
            if (i > 0) b.append(Component.text(" · ", t.subAltColor))
            b.append(
                Component.text(c, if (c == current) t.mainColor else t.subColor)
                    .clickEvent(ClickEvent.runCommand("/settings ${setting.id} $c"))
                    .hoverEvent(HoverEvent.showText(Component.text("/settings ${setting.id} $c", t.textColor))),
            )
        }
        if (shown.size < choices.size) {
            b.append(Component.text(" · … ${choices.size - shown.size} more (", t.subAltColor))
            b.append(Component.text("${choices.size} total, type /settings ${setting.id} <name>", t.subColor))
            b.append(Component.text(")", t.subAltColor))
        }
        return b.build()
    }

    /** All settings grouped by section, each name clickable (fills in the command). */
    private fun list(t: AbstractTheme): Component {
        val b = Component.text()
        b.append(Component.text("» ", t.caretColor), Component.text("settings", t.textColor).decorate(TextDecoration.BOLD))
        for (section in Section.entries) {
            val ids = SettingsCatalog.all.filter { it.section == section && it.id != "reset-settings" }
            if (ids.isEmpty()) continue
            b.append(Component.newline(), indent(t), Component.text(section.label, t.mainColor), Component.text(": ", t.subColor))
            ids.forEachIndexed { i, s ->
                if (i > 0) b.append(Component.text(", ", t.subAltColor))
                b.append(Component.text(s.id, t.subColor).clickEvent(ClickEvent.suggestCommand("/settings ${s.id} ")))
            }
        }
        b.append(
            Component.newline(), indent(t), Component.text("/settings ", t.mainColor), Component.text("<setting> [value]", t.subColor),
            Component.text("  or  ", t.subAltColor), Component.text("/settings reset", t.mainColor),
        )
        return b.build()
    }

    private fun indent(t: AbstractTheme): Component = Component.text("  ", t.subAltColor)

    private fun line(t: AbstractTheme, vararg parts: Component): Component =
        Component.text().append(Component.text("» ", t.caretColor)).append(parts.toList()).build()
}
