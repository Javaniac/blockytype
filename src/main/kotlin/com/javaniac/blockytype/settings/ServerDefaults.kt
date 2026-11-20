package com.javaniac.blockytype.settings

import com.javaniac.blockytype.BlockyType
import com.javaniac.blockytype.pack.BoxColors
import com.javaniac.blockytype.pack.PackVariant
import com.javaniac.blockytype.themes.AbstractTheme
import com.javaniac.blockytype.themes.DefaultTheme
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("ServerDefaults")

/**
 * Server wide defaults edited in the admin panel, cached in memory (read on the tick thread) and stored in
 * the settings file ([ServerSettings]). Only settings with `serverDefault = true` are kept here.
 */
object ServerDefaults {
    private val values = ConcurrentHashMap<String, String>()

    /** Loads the stored defaults, dropping keys that are no longer valid server defaults. */
    fun load(stored: Map<String, String>) {
        values.clear()
        for ((id, value) in stored) {
            if (SettingsCatalog.byId(id)?.serverDefault == true) values[id] = value
        }
    }

    /** The admin's default for [id] (from the settings file), or null when none is loaded. */
    operator fun get(id: String): String? = values[id]

    /** The value a player sees: their own choice, then the server default, then the catalog default. */
    fun effective(id: String, user: Map<String, String>): String =
        user[id] ?: this[id] ?: SettingsCatalog.byId(id)!!.default

    /** The pack variant of a player with the settings [user] (own choices only, rest is default). */
    fun packVariant(user: Map<String, String>) = PackVariant.of(
        font = effective("font", user), antiAlias = effective("anti-aliasing", user), inputBox = effective("input-box", user),
        box = BoxColors.of(themeOf(effective("theme", user))),
    )

    private fun themeOf(name: String): AbstractTheme =
        BlockyType.themes.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DefaultTheme

    /** Puts the default of [id] back to the catalog default (also in the file). Admin only, checked by the caller. */
    fun clear(id: String) {
        try {
            ServerSettings.file.reset(id)
            values[id] = SettingsCatalog.byId(id)!!.default
        } catch (t: Throwable) {
            log.error("Could not clear server default $id", t)
        }
    }

    fun snapshot(): Map<String, String> = HashMap(values)

    /** Sets (and stores) the default of [id]. The caller has checked that the player is an admin. */
    fun set(id: String, value: String) {
        require(SettingsCatalog.byId(id)?.serverDefault == true) { "$id has no server default" }
        try {
            ServerSettings.file.set(id, value)
            values[id] = value
        } catch (t: Throwable) {
            log.error("Could not save server default $id", t)
        }
    }
}
