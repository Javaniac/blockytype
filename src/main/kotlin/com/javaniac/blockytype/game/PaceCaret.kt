package com.javaniac.blockytype.game

import com.javaniac.blockytype.db.PlayerStats

/** The speed of the pace caret for the `pace-caret` setting, from the player's results (null: no pace caret). */
object PaceCaret {
    /** [mode] is `off`, `average`, `pb`, `last` or `custom`; [custom] the `pace-caret-custom` wpm. */
    fun target(mode: String, custom: Int, stats: PlayerStats?): Double? = when (mode) {
        "average" -> stats?.average
        "pb" -> stats?.best
        "last" -> stats?.last
        "custom" -> custom.toDouble().takeIf { it > 0 }
        else -> null
    }
}
