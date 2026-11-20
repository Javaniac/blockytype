package com.javaniac.blockytype.game

/**
 * Re-reading the player's keybinds now and then: a client cannot tell the server it changed a key, so the probe
 * anvil is opened again every `keybind-check` interval, but only while the player is idle.
 */
object KeybindPoll {
    const val DEFAULT_MILLIS = 5L * 60_000

    /** The interval of a `keybind-check` value (`5 min`), or null when it is off. */
    fun intervalMillis(option: String): Long? {
        if (option == "off") return null
        return option.substringBefore(' ').toLongOrNull()?.takeIf { it > 0 }?.times(60_000) ?: DEFAULT_MILLIS
    }

    /** True when a check is due: its time came and nothing (test, menu, result, typing screen) would be interrupted. */
    fun due(now: Long, nextAt: Long, idle: Boolean): Boolean = idle && now >= nextAt

    /** When to check again, or null when the check is off. */
    fun next(now: Long, intervalMillis: Long?): Long? = intervalMillis?.let { now + it }
}
