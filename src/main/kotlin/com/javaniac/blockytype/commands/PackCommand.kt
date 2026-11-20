package com.javaniac.blockytype.commands

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.pack.FontDownloader
import com.javaniac.blockytype.pack.FontRegistry
import com.javaniac.blockytype.settings.ServerDefaults
import com.javaniac.blockytype.settings.ServerSettings
import com.javaniac.blockytype.sendRichMessage
import com.javaniac.blockytype.withMinestomMain
import kotlinx.coroutines.launch
import net.minestom.server.command.CommandSender
import net.minestom.server.command.ConsoleSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("PackCommand")

/**
 * `pack reload`, `pack input show|hide`, `pack font list|download <url> [name]|use <name>|reset`.
 * Console and admins (`/grantadmin`) only.
 */
object PackCommand : Command("pack") {
    fun isAdmin(sender: CommandSender) =
        sender is ConsoleSender || (sender is Player && ServerConfig.isAdmin(sender.uuid))

    private fun reload(sender: CommandSender, what: String) {
        try {
            val sha1 = BlockyType.reloadPacks()
            sender.sendRichMessage("$what. Pack reloaded (sha1 $sha1)")
        } catch (t: Throwable) {
            log.error("Pack reload failed", t)
            sender.sendRichMessage("Pack reload failed: ${t.message}")
        }
    }

    init {
        setCondition { sender, _ -> isAdmin(sender) }

        val reload = ArgumentType.Literal("reload")
        val font = ArgumentType.Literal("font")
        val list = ArgumentType.Literal("list")
        val download = ArgumentType.Literal("download")
        val use = ArgumentType.Literal("use")
        val reset = ArgumentType.Literal("reset")
        val url = ArgumentType.String("url")
        val name = ArgumentType.String("name")

        addSyntax({ sender, _ ->
            ServerSettings.load() // hand edits of blockytype.properties
            reload(sender, "Settings file re-read")
        }, reload)

        addSyntax({ sender, _ ->
            sender.sendRichMessage("Fonts: ${FontRegistry.names().joinToString(", ")} (server default: ${ServerDefaults["font"]})")
        }, font, list)

        val downloadFont = { sender: CommandSender, context: net.minestom.server.command.builder.CommandContext ->
            val link: String = context.get(url)
            val requested: String? = if (context.has(name)) context.get(name) else null
            sender.sendRichMessage("Downloading font...")
            BlockyType.launch {
                try {
                    val info = FontDownloader.download(link, requested)
                    log.info("Downloaded font '${info.name}'; the font's license is the admin's responsibility.")
                    withMinestomMain {
                        sender.sendRichMessage("Saved font '${info.name}'. Use it with: pack font use ${info.name}")
                    }
                } catch (t: Throwable) {
                    withMinestomMain { sender.sendRichMessage("Font download failed: ${t.message}") }
                }
            }
            Unit
        }
        addSyntax(downloadFont, font, download, url)
        addSyntax(downloadFont, font, download, url, name)

        addSyntax({ sender, context ->
            val fontName: String = context.get(name)
            try {
                FontRegistry.load(fontName) // validates
                ServerDefaults.set("font", fontName)
                reload(sender, "Default font is now '$fontName'")
            } catch (t: Throwable) {
                sender.sendRichMessage("Cannot use font: ${t.message}")
            }
        }, font, use, name)

        val input = ArgumentType.Literal("input")
        val show = ArgumentType.Literal("show")
        val hide = ArgumentType.Literal("hide")
        addSyntax({ sender, _ ->
            ServerDefaults.set("input-box", "show")
            reload(sender, "Command box input visible by default")
        }, input, show)
        addSyntax({ sender, _ ->
            ServerDefaults.set("input-box", "hide")
            reload(sender, "Command box input hidden by default")
        }, input, hide)

        addSyntax({ sender, _ ->
            ServerDefaults.clear("font")
            reload(sender, "Default font reset to '${ServerDefaults["font"]}'")
        }, font, reset)
    }
}
