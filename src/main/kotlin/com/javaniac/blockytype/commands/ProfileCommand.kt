package com.javaniac.blockytype.commands

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.profile.Lookup
import com.javaniac.blockytype.profile.ProfileService
import com.javaniac.blockytype.withMinestomMain
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ProfileCommand")

/**
 * `/profile` opens your own profile, `/profile <player>` the profile of a player that joined this server
 * (online or not). A name nobody joined with gets a short message instead.
 */
object ProfileCommand : Command("profile") {
    init {
        setCondition { sender, _ -> sender is Player }

        setDefaultExecutor { sender, _ ->
            val player = sender as? Player ?: return@setDefaultExecutor
            Sessions[player]?.openProfile(player.uuid)
        }

        val name = ArgumentType.Word("player")
        addSyntax({ sender, context ->
            val player = sender as? Player ?: return@addSyntax
            val session = Sessions[player] ?: return@addSyntax
            val typed = context.get(name)
            BlockyType.launch {
                val found = try {
                    ProfileService.lookup(typed)
                } catch (t: Throwable) {
                    log.error("Could not look up $typed", t)
                    Lookup.Unknown
                }
                withMinestomMain {
                    when (found) {
                        is Lookup.Found -> session.openProfile(found.player.uuid)
                        Lookup.Unknown -> player.sendMessage(problem(session, "$typed never joined"))
                        Lookup.Invalid -> player.sendMessage(problem(session, "$typed is not a valid name"))
                    }
                }
            }
        }, name)
    }

    private fun problem(session: com.javaniac.blockytype.game.TestSession, text: String): Component =
        Component.text().append(Component.text("» ", session.theme.caretColor)).append(Component.text(text, session.theme.errorColor)).build()
}
