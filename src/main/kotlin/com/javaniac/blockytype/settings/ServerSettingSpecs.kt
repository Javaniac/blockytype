package com.javaniac.blockytype.settings

import com.javaniac.blockytype.NetworkSettings
import com.javaniac.blockytype.StartupArgs

/** The options of the server settings file, in file order: the network settings, then the server defaults. */
object ServerSettingSpecs {
    private val DEFAULT_IDS = listOf("font", "input-box", "animation", "anti-aliasing", "theme")

    fun all(): List<SettingSpec> = network() + defaults()

    private fun network() = listOf(
        net("server-ip", "IP the game server binds to. 0.0.0.0 means every network interface.\nNeeds a restart.", StartupArgs.DEFAULT_SERVER_IP),
        net("server-port", "Port of the game server (Minecraft clients connect here).\nNeeds a restart.", StartupArgs.DEFAULT_SERVER_PORT.toString()),
        net(
            "resource-pack-ip",
            "Address players download the resource pack from. It must be reachable by the players (public IP or domain).\n" +
                "Players on the server's own machine are sent to 127.0.0.1 automatically. Needs a restart.",
            StartupArgs.DEFAULT_RESOURCE_PACK_IP,
        ),
        net(
            "resource-pack-port",
            "Port of the resource pack web server. Forward it on your router if players join from outside.\nNeeds a restart.",
            StartupArgs.DEFAULT_RESOURCE_PACK_PORT.toString(),
        ),
    )

    private fun net(key: String, comment: String, default: String) =
        SettingSpec(key, comment, default) { NetworkSettings.validate(key, it) != null }

    private fun defaults() = DEFAULT_IDS.map { id ->
        val setting = SettingsCatalog.byId(id)!!
        val choices = setting.options.takeIf { it.isFixed }?.values()
        val comment = "Server default: " + setting.description.substringBefore(". Admins").trimEnd('.') + "." +
            (choices?.let { "\nChoices: ${it.joinToString(", ")}. Players can override it." } ?: "\nPlayers can override it.")
        SettingSpec(id, comment, setting.default) { v -> v.isNotBlank() && (choices == null || v in choices) }
    }
}
