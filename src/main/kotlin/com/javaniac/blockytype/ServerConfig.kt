package com.javaniac.blockytype

import java.util.UUID

/** Who may run admin commands and open the admin panel. The admin list lives in the database (`/grantadmin` in the console). */
object ServerConfig {
    /** UUIDs allowed to run admin commands and open the admin panel. The console always can. */
    @Volatile
    var admins: Set<UUID> = emptySet()
        private set

    fun isAdmin(uuid: UUID) = uuid in admins

    fun grantAdmin(uuid: UUID) {
        admins = admins + uuid
    }

    fun revokeAdmin(uuid: UUID) {
        admins = admins - uuid
    }

    /** Sets the admins stored in the database. Call after `Storage.init`. */
    fun loadAdmins(stored: Collection<UUID>) {
        admins = stored.toSet()
    }
}
