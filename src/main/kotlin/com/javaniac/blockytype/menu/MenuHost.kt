package com.javaniac.blockytype.menu

import com.javaniac.blockytype.NetworkField
import com.javaniac.blockytype.settings.Section
import java.util.UUID

/** What a menu needs from the game. Implemented per player; a fake in tests. */
interface MenuHost {
    /** Re-checked on every admin action, never cached by the menu. */
    val isAdmin: Boolean

    /** The player's own choice for setting [id], or null when they follow the server default. */
    fun userValue(id: String): String?

    /** The server default an admin set for [id], or null for the catalog default. */
    fun serverValue(id: String): String?

    fun setUser(id: String, value: String)

    fun clearUser(id: String)

    /** Admin only (the menu checks [isAdmin] first, the host must check again). */
    fun setServer(id: String, value: String)

    fun resetUserSettings()

    /** The admin's network settings for the panel. */
    fun networkFields(): List<NetworkField>

    /** The value being typed into the command block screen, or null. */
    val editing: FieldEdit?

    /** Starts typing a network value (admin only, the host checks again and that the key is editable). */
    fun beginEdit(key: String)

    /** The player's own id: the profile their profile button opens. */
    val selfId: UUID get() = UUID(0, 0)

    /** The profile of [uuid]: loaded in the background, so it can still be [ProfileView.Loading]. */
    fun profileOf(uuid: UUID): ProfileView = ProfileView.Missing

    /** Starts typing the name of a player to look up (any player may). */
    fun beginLookup() {}

    /** [wpm] in the player's speed unit and decimals setting. */
    fun speed(wpm: Double): String = Math.round(wpm).toString()

    /** [value] (a percentage) as the player wants numbers shown. */
    fun percent(value: Double): String = "${Math.round(value)}%"

    fun now(): Long = System.currentTimeMillis()

    fun restartTest()

    fun close()

    /** Valid values of [id], including the dynamic lists (installed themes and fonts). */
    fun choices(id: String): List<String>
}

/** A screen of the menu. */
sealed interface Page {
    /** The settings page, one section at a time. */
    data class Settings(val section: Section) : Page

    /** The admin tab of the settings page. */
    data object Admin : Page

    /** One page ([index], 0 based) of the language list. */
    data class Languages(val index: Int) : Page

    /** The profile of a player, [recent] = page of their recent tests (0 based). */
    data class Profile(val uuid: UUID, val recent: Int = 0) : Page
}
