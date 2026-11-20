package com.javaniac.blockytype

import com.javaniac.blockytype.themes.DefaultTheme
import com.javaniac.blockytype.ui.Logo
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.event.server.ServerListPingEvent
import net.minestom.server.ping.Status

/** How the server presents itself: the brand in the F3 screen, and the name and logo in the server list. */
object ServerBrand {
    const val NAME = "BlockyType"

    fun motd(): Component = Component.text()
        .append(Component.text("Blocky", DefaultTheme.textColor))
        .append(Component.text("Type", DefaultTheme.mainColor))
        .append(Component.text("  a typing test inside Minecraft", DefaultTheme.subColor))
        .build()

    /** The server list icon: the logo at 64 x 64. */
    fun icon(): ByteArray = Logo.png(DefaultTheme, 64)

    private val iconBytes by lazy { icon() }

    fun install() {
        MinecraftServer.setBrandName(NAME)
        MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent::class.java) { e ->
            e.status = Status.builder(e.status).description(motd()).favicon(iconBytes).build()
        }
    }
}
