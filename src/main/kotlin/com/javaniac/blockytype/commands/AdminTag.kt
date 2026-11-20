package com.javaniac.blockytype.commands

import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.game.Sessions
import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.themes.DefaultTheme
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player

/** The `[Server Admin]` prefix of players with administrator permissions: in chat, the tab list and profiles. */
object AdminTag {
    const val TEXT = "[Server Admin]"

    /** The tag in the accent color of [theme]. */
    fun tag(theme: AbstractTheme): Component = Component.text(TEXT, theme.mainColor)

    /** The tab list name: the tag in front of an admin's name. */
    fun displayName(theme: AbstractTheme, name: String, admin: Boolean): Component =
        if (admin) Component.text().append(tag(theme)).append(Component.text(" $name", theme.textColor)).build()
        else Component.text(name, theme.textColor)

    /** Sets the tab list name of [player] from their current admin state and theme. */
    fun refresh(player: Player) {
        val theme = Sessions[player]?.theme ?: DefaultTheme
        player.displayName = displayName(theme, player.username, ServerConfig.isAdmin(player.uuid))
    }
}
