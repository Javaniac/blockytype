package com.javaniac.blockytype.settings

import com.javaniac.blockytype.NetworkPanel
import java.nio.file.Path

/** The server settings file ([SettingsFile], `blockytype.properties`) and the caches that are filled from it. */
object ServerSettings {
    var file: SettingsFile = SettingsFile(Path.of("blockytype.properties"), ServerSettingSpecs.all())

    /**
     * Reads the file into [ServerDefaults] and [NetworkPanel] and returns the valid values. [legacy] are the values of
     * the old `server_settings` table, used only for options the file does not have yet. Also used to pick up hand edits.
     */
    fun load(legacy: Map<String, String> = emptyMap()): Map<String, String> =
        file.load(legacy).also {
            ServerDefaults.load(it)
            NetworkPanel.load(it)
        }

    /** Saves a network [key] (applies at the next start). Throws [IllegalArgumentException] for invalid values. */
    fun setNetwork(key: String, value: String) {
        file.set(key, value)
        NetworkPanel.set(key, value)
    }
}
