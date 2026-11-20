package com.javaniac.blockytype.profile

import com.javaniac.blockytype.commands.Profile
import com.javaniac.blockytype.db.Storage

/**
 * Players that played before the players table existed have results and settings but no row, so a profile lookup
 * would call them unknown. This adds them: admins by their stored name, everybody else by asking Mojang.
 */
object PlayerBackfill {
    /** Adds the missing players. [resolve] gives the profile of a uuid (blocking, null when unknown). Returns how many were added. */
    suspend fun run(resolve: (java.util.UUID) -> Profile?): Int {
        val admins = Storage.admins().associate { it.uuid to it.name }
        var added = 0
        for (uuid in Storage.playersWithoutRow()) {
            val name = admins[uuid] ?: resolve(uuid)?.name ?: continue
            val first = Storage.profileResults(uuid).minOfOrNull { it.at }
            Storage.touchPlayer(uuid, name, now = first ?: System.currentTimeMillis(), firstSeen = first)
            added++
        }
        return added
    }
}
