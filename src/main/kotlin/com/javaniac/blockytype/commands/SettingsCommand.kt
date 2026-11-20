package com.javaniac.blockytype.commands

import com.javaniac.blockytype.game.PlayerMenuHost
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.menu.Page
import com.javaniac.blockytype.settings.Section
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

/**
 * `/settings` opens the settings page. `/settings <setting> [value]` shows or changes one of the player's own
 * settings (`default` follows the server default again, `/settings reset` clears all). See [SettingsChat].
 */
object SettingsCommand : Command("settings") {
    init {
        setCondition { sender, _ -> sender is Player }

        setDefaultExecutor { sender, _ ->
            val player = sender as? Player ?: return@setDefaultExecutor
            Sessions[player]?.openMenu(Page.Settings(Section.BEHAVIOR))
        }

        val setting = ArgumentType.String("setting")
        val value = ArgumentType.StringArray("value")

        fun run(sender: net.minestom.server.command.CommandSender, args: List<String>) {
            val player = sender as? Player ?: return
            val session = Sessions[player] ?: return
            for (line in SettingsChat.run(PlayerMenuHost(session), args) { session.theme }) sender.sendMessage(line)
        }

        addSyntax({ sender, context -> run(sender, listOf(context.get(setting))) }, setting)
        addSyntax({ sender, context -> run(sender, listOf(context.get(setting)) + context.get(value).toList()) }, setting, value)
    }
}

/** `/restart`: starts a fresh test (same as F). */
object RestartCommand : Command("restart") {
    init {
        setCondition { sender, _ -> sender is Player }
        setDefaultExecutor { sender, _ ->
            val player = sender as? Player ?: return@setDefaultExecutor
            Sessions[player]?.takeIf { it.menu == null }?.reset()
        }
    }
}
