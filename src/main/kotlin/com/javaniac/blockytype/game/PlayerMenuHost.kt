package com.javaniac.blockytype.game

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.NetworkPanel
import com.javaniac.blockytype.NetworkSettings
import com.javaniac.blockytype.menu.FieldEdit
import com.javaniac.blockytype.menu.MenuHost
import com.javaniac.blockytype.menu.ProfileView
import java.util.UUID
import com.javaniac.blockytype.pack.FontRegistry
import com.javaniac.blockytype.settings.ServerDefaults
import com.javaniac.blockytype.settings.SettingsCatalog

/** [MenuHost] of one player's [TestSession]. */
class PlayerMenuHost(private val session: TestSession) : MenuHost {
    override val isAdmin: Boolean get() = ServerConfig.isAdmin(session.player.uuid)

    override fun userValue(id: String): String? = session.settings.overrides[id]

    override fun serverValue(id: String): String? = ServerDefaults[id]

    override fun setUser(id: String, value: String) {
        if (id == "language") session.setLanguage(value) else session.updateSettings { it.with(id, value) }
    }

    override fun clearUser(id: String) = session.updateSettings { it.without(id) }

    override fun setServer(id: String, value: String) {
        if (!isAdmin) return // checked again: never trust the caller
        ServerDefaults.set(id, value)
        session.settingsChanged()
        Sessions.all().filter { it !== session }.forEach { it.settingsChanged() }
    }

    override fun networkFields() = NetworkSettings.fields(BlockyType.network, NetworkPanel.snapshot())

    override val editing: FieldEdit? get() = session.edit

    override fun beginEdit(key: String) {
        if (!isAdmin) return // checked again: never trust the caller
        if (networkFields().none { it.key == key }) return
        session.beginEdit(key)
    }

    override val selfId: UUID get() = session.player.uuid

    override fun profileOf(uuid: UUID): ProfileView = session.profileView(uuid)

    override fun beginLookup() = session.beginLookup()

    override fun speed(wpm: Double): String = session.view.let { it.unit.format(wpm, it.decimals) }

    override fun percent(value: Double): String =
        if (session.view.decimals) "%.2f%%".format(java.util.Locale.ROOT, value) else "${Math.round(value)}%"

    override fun resetUserSettings() = session.updateSettings { PlayerSettings() }

    override fun restartTest() = session.reset()

    override fun close() = session.closeMenu()

    override fun choices(id: String): List<String> = when (id) {
        "theme" -> BlockyType.themes.map { it.name.lowercase() }
        "font" -> FontRegistry.names()
        else -> SettingsCatalog.byId(id)?.options?.values() ?: emptyList()
    }
}
