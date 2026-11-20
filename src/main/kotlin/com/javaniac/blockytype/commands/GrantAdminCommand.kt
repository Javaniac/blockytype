package com.javaniac.blockytype.commands

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.sendRichMessage
import com.javaniac.blockytype.withMinestomMain
import kotlinx.coroutines.launch
import net.minestom.server.MinecraftServer
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("GrantAdminCommand")

/**
 * `grantadmin <username>` and `revokeadmin <username>`. Console only: a player can never run these,
 * not even an admin. Admins get the "Admin Settings" button in the UI.
 */
abstract class AdminChangeCommand(name: String, private val grant: Boolean) : Command(name) {
    init {
        setCondition { sender, _ -> sender is ConsoleSender }
        val username = ArgumentType.Word("username")
        addSyntax({ sender, context -> run(sender, context.get(username)) }, username)
        setDefaultExecutor { sender, _ -> sender.sendRichMessage("Usage: $name <username>") }
    }

    private fun run(sender: CommandSender, name: String) {
        if (!MojangProfiles.isValidName(name)) {
            sender.sendRichMessage("'$name' is not a valid Minecraft username")
            return
        }
        val online = MinecraftServer.getConnectionManager().onlinePlayers
            .firstOrNull { it.username.equals(name, ignoreCase = true) }
        BlockyType.launch {
            try {
                val profile = if (online != null) Profile(online.uuid, online.username) else MojangProfiles.lookup(name)
                if (profile == null) {
                    withMinestomMain { sender.sendRichMessage("No Minecraft account named '$name'") }
                    return@launch
                }
                if (grant) {
                    Storage.addAdmin(profile.uuid, profile.name)
                } else {
                    Storage.removeAdmin(profile.uuid)
                }
                withMinestomMain {
                    if (grant) ServerConfig.grantAdmin(profile.uuid) else ServerConfig.revokeAdmin(profile.uuid)
                    MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(profile.uuid)?.let(AdminTag::refresh)
                    sender.sendRichMessage(
                        if (grant) "${profile.name} (${profile.uuid}) is now an admin"
                        else "${profile.name} (${profile.uuid}) is no longer an admin"
                    )
                }
            } catch (t: Throwable) {
                log.error("Changing admin failed", t)
                withMinestomMain { sender.sendRichMessage("Failed: ${t.message}") }
            }
        }
    }
}

object GrantAdminCommand : AdminChangeCommand("grantadmin", grant = true)

object RevokeAdminCommand : AdminChangeCommand("revokeadmin", grant = false)
