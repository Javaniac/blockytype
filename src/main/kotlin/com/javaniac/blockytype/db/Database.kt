@file:OptIn(ExperimentalUuidApi::class)

package com.javaniac.blockytype.db

import com.javaniac.blockytype.game.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.core.SortOrder
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** The numbers of a saved test that the averages and the pace caret need. */
data class SavedResult(val mode: String, val wpm: Double, val accuracy: Double)

/** Speed targets and averages of a player, from [SavedResult]s (newest first). */
data class PlayerStats(val best: Double?, val last: Double?, val average: Double?, val accuracy: Double?) {
    companion object {
        /** [mode] limits last and average to that mode (null = all modes); [best] is passed in (see `Storage.bestWpm`). */
        fun of(recent: List<SavedResult>, mode: String?, best: Double?): PlayerStats {
            val inMode = if (mode == null) recent else recent.filter { it.mode == mode }
            return PlayerStats(
                best = best,
                last = inMode.firstOrNull()?.wpm,
                average = inMode.takeIf { it.isNotEmpty() }?.map { it.wpm }?.average(),
                accuracy = inMode.takeIf { it.isNotEmpty() }?.map { it.accuracy }?.average(),
            )
        }
    }
}

/** One saved test. */
object TestResults : Table("test_results") {
    val id = long("id").autoIncrement()
    val player = uuid("player").index()
    val createdAt = long("created_at")
    val mode = varchar("mode", 64)
    val wpm = double("wpm")
    val raw = double("raw")
    val accuracy = double("accuracy")
    val consistency = double("consistency")
    val seconds = double("seconds")
    val correct = integer("correct")
    val incorrect = integer("incorrect")
    val extra = integer("extra")
    val missed = integer("missed")

    override val primaryKey = PrimaryKey(id)
}

/** A player that joined this server at least once: who a profile can be looked up for. */
object PlayersTable : Table("players") {
    val uuid = uuid("uuid")
    val name = varchar("name", 32)
    val nameLower = varchar("name_lower", 32).index()
    val firstSeen = long("first_seen")
    val lastSeen = long("last_seen")

    override val primaryKey = PrimaryKey(uuid)
}

/** The 8 x 8 face of a player's skin (64 RGB values, 4 bytes each), cached so profiles open without a download. */
object PlayerFacesTable : Table("player_faces") {
    val uuid = uuid("uuid")
    val pixels = binary("pixels")
    val fetchedAt = long("fetched_at")

    override val primaryKey = PrimaryKey(uuid)
}

/** A player the server has seen. */
data class KnownPlayer(val uuid: UUID, val name: String, val firstSeen: Long, val lastSeen: Long)

/** Old per-player settings table (one column per setting). Read once by [Storage.migrateLegacySettings], then emptied. */
object PlayerSettingsTable : Table("player_settings") {
    val player = uuid("player")
    val theme = varchar("theme", 64).default(PlayerSettings.DEFAULT_THEME)

    override val primaryKey = PrimaryKey(player)
}

/** A setting a player changed. A missing row means "use the server default". Keys come from the settings catalog. */
object PlayerSettingTable : Table("player_setting") {
    val player = uuid("player")
    val key = varchar("key", 64)
    val value = varchar("value", 512)

    override val primaryKey = PrimaryKey(player, key)
}

/** Server administrators, added with `/grantadmin` in the console. */
object AdminsTable : Table("admins") {
    val uuid = uuid("uuid")
    val name = varchar("name", 32)

    override val primaryKey = PrimaryKey(uuid)
}

/** Server wide defaults edited in the admin panel (key to value, see the settings catalog). */
object ServerSettingsTable : Table("server_settings") {
    val key = varchar("key", 64)
    val value = varchar("value", 512)

    override val primaryKey = PrimaryKey(key)
}

data class Admin(val uuid: UUID, val name: String)

/**
 * Settings a player changed from the server defaults, key to value (keys from the settings catalog).
 * Anything not in [overrides] follows the server default.
 */
data class PlayerSettings(val overrides: Map<String, String> = emptyMap()) {
    val theme: String get() = overrides["theme"] ?: DEFAULT_THEME

    fun with(key: String, value: String) = PlayerSettings(overrides + (key to value))

    fun without(key: String) = PlayerSettings(overrides - key)

    companion object {
        const val DEFAULT_THEME = "default"
    }
}

/**
 * SQLite storage (Exposed). All functions are suspending and run on the IO dispatcher, so they are safe
 * to call from the Minestom tick thread via `BlockyType.launch`, never block it.
 */
object Storage {
    fun init(file: String = "blockytype.db") {
        Database.connect("jdbc:sqlite:$file", "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(TestResults, PlayerSettingsTable, PlayerSettingTable, AdminsTable, ServerSettingsTable, PlayersTable, PlayerFacesTable)
        }
        runBlocking { migrateLegacySettings() }
    }

    /** Moves non-default themes from the old `player_settings` table into `player_setting`, then empties the old one. */
    suspend fun migrateLegacySettings() = withContext(Dispatchers.IO) {
        transaction {
            val legacy = PlayerSettingsTable.selectAll().map { it[PlayerSettingsTable.player] to it[PlayerSettingsTable.theme] }
            for ((player, theme) in legacy) {
                if (theme != PlayerSettings.DEFAULT_THEME) {
                    PlayerSettingTable.upsert {
                        it[PlayerSettingTable.player] = player
                        it[key] = "theme"
                        it[value] = theme
                    }
                }
            }
            PlayerSettingsTable.deleteAll()
        }
        Unit
    }

    /** Test helper: writes a row in the old table. */
    suspend fun insertLegacyTheme(player: UUID, theme: String) = withContext(Dispatchers.IO) {
        transaction {
            PlayerSettingsTable.upsert {
                it[PlayerSettingsTable.player] = player.toKotlinUuid()
                it[PlayerSettingsTable.theme] = theme
            }
        }
        Unit
    }

    suspend fun admins(): List<Admin> = withContext(Dispatchers.IO) {
        transaction {
            AdminsTable.selectAll().map { Admin(it[AdminsTable.uuid].toJavaUuid(), it[AdminsTable.name]) }
        }
    }

    suspend fun addAdmin(uuid: UUID, name: String) = withContext(Dispatchers.IO) {
        transaction {
            AdminsTable.upsert {
                it[AdminsTable.uuid] = uuid.toKotlinUuid()
                it[AdminsTable.name] = name
            }
        }
        Unit
    }

    /** True if [uuid] was an admin. */
    suspend fun removeAdmin(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction { AdminsTable.deleteWhere { AdminsTable.uuid eq uuid.toKotlinUuid() } > 0 }
    }

    suspend fun serverSetting(key: String): String? = withContext(Dispatchers.IO) {
        transaction {
            ServerSettingsTable.selectAll().where { ServerSettingsTable.key eq key }.firstOrNull()
                ?.get(ServerSettingsTable.value)
        }
    }

    suspend fun serverSettings(): Map<String, String> = withContext(Dispatchers.IO) {
        transaction {
            ServerSettingsTable.selectAll().associate { it[ServerSettingsTable.key] to it[ServerSettingsTable.value] }
        }
    }

    suspend fun setServerSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        transaction {
            ServerSettingsTable.upsert {
                it[ServerSettingsTable.key] = key
                it[ServerSettingsTable.value] = value
            }
        }
        Unit
    }

    suspend fun clearServerSetting(key: String) = withContext(Dispatchers.IO) {
        transaction { ServerSettingsTable.deleteWhere { ServerSettingsTable.key eq key } }
        Unit
    }

    suspend fun saveResult(player: UUID, mode: String, result: TestResult) = withContext(Dispatchers.IO) {
        transaction {
            TestResults.insert {
                it[TestResults.player] = player.toKotlinUuid()
                it[createdAt] = System.currentTimeMillis()
                it[TestResults.mode] = mode
                it[wpm] = result.wpm
                it[raw] = result.raw
                it[accuracy] = result.accuracy
                it[consistency] = result.consistency
                it[seconds] = result.seconds
                it[correct] = result.counts.correct
                it[incorrect] = result.counts.incorrect
                it[extra] = result.counts.extra
                it[missed] = result.counts.missed
            }
        }
        Unit
    }

    /** The last [limit] results of [player], newest first. */
    suspend fun recentResults(player: UUID, limit: Int): List<SavedResult> = withContext(Dispatchers.IO) {
        transaction {
            TestResults.selectAll().where { TestResults.player eq player.toKotlinUuid() }
                .orderBy(TestResults.id, SortOrder.DESC).limit(limit)
                .map { SavedResult(it[TestResults.mode], it[TestResults.wpm], it[TestResults.accuracy]) }
        }
    }

    /** Best wpm of [player] for [mode], or null if none saved. */
    suspend fun bestWpm(player: UUID, mode: String): Double? = withContext(Dispatchers.IO) {
        transaction {
            TestResults.select(TestResults.wpm.max())
                .where { (TestResults.player eq player.toKotlinUuid()) and (TestResults.mode eq mode) }
                .firstOrNull()?.get(TestResults.wpm.max())
        }
    }

    // ---- players and profiles

    /**
     * Records that [uuid] joined as [name]. The first time stays; [firstSeen] (for a backfill) is used for a player
     * that has no row yet.
     */
    suspend fun touchPlayer(uuid: UUID, name: String, now: Long = System.currentTimeMillis(), firstSeen: Long? = null) =
        withContext(Dispatchers.IO) {
            transaction {
                val id = uuid.toKotlinUuid()
                val old = PlayersTable.selectAll().where { PlayersTable.uuid eq id }.firstOrNull()
                PlayersTable.upsert {
                    it[PlayersTable.uuid] = id
                    it[PlayersTable.name] = name
                    it[nameLower] = name.lowercase()
                    it[PlayersTable.firstSeen] = old?.get(PlayersTable.firstSeen) ?: firstSeen ?: now
                    it[lastSeen] = now
                }
            }
            Unit
        }

    private fun known(row: org.jetbrains.exposed.v1.core.ResultRow) = KnownPlayer(
        row[PlayersTable.uuid].toJavaUuid(), row[PlayersTable.name], row[PlayersTable.firstSeen], row[PlayersTable.lastSeen],
    )

    /** The player with this exact name (any case), or null: a player that never joined is not found. */
    suspend fun playerByName(name: String): KnownPlayer? = withContext(Dispatchers.IO) {
        transaction {
            PlayersTable.selectAll().where { PlayersTable.nameLower eq name.lowercase() }
                .orderBy(PlayersTable.lastSeen, SortOrder.DESC).limit(1).firstOrNull()?.let(::known)
        }
    }

    suspend fun playerByUuid(uuid: UUID): KnownPlayer? = withContext(Dispatchers.IO) {
        transaction { PlayersTable.selectAll().where { PlayersTable.uuid eq uuid.toKotlinUuid() }.firstOrNull()?.let(::known) }
    }

    /** Every saved test of [player] (any order), for the profile. */
    suspend fun profileResults(player: UUID): List<com.javaniac.blockytype.profile.ResultRow> = withContext(Dispatchers.IO) {
        transaction {
            TestResults.selectAll().where { TestResults.player eq player.toKotlinUuid() }.map {
                com.javaniac.blockytype.profile.ResultRow(
                    it[TestResults.mode], it[TestResults.wpm], it[TestResults.raw], it[TestResults.accuracy],
                    it[TestResults.consistency], it[TestResults.seconds], it[TestResults.createdAt],
                )
            }
        }
    }

    /** Players that have results or settings but no row in the players table (they joined before it existed). */
    suspend fun playersWithoutRow(): List<UUID> = withContext(Dispatchers.IO) {
        transaction {
            val known = PlayersTable.selectAll().map { it[PlayersTable.uuid] }.toSet()
            val withData = TestResults.select(TestResults.player).withDistinct().map { it[TestResults.player] } +
                PlayerSettingTable.select(PlayerSettingTable.player).withDistinct().map { it[PlayerSettingTable.player] }
            withData.filter { it !in known }.distinct().map { it.toJavaUuid() }
        }
    }

    /** The cached face of [uuid] if it is younger than [maxAgeMillis]. */
    suspend fun face(uuid: UUID, now: Long = System.currentTimeMillis(), maxAgeMillis: Long): IntArray? = withContext(Dispatchers.IO) {
        transaction {
            val row = PlayerFacesTable.selectAll().where { PlayerFacesTable.uuid eq uuid.toKotlinUuid() }.firstOrNull() ?: return@transaction null
            if (now - row[PlayerFacesTable.fetchedAt] > maxAgeMillis) return@transaction null
            val bytes = row[PlayerFacesTable.pixels]
            if (bytes.size != 64 * 4) return@transaction null
            val buf = java.nio.ByteBuffer.wrap(bytes)
            IntArray(64) { buf.getInt() }
        }
    }

    suspend fun saveFace(uuid: UUID, pixels: IntArray, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        require(pixels.size == 64)
        val buf = java.nio.ByteBuffer.allocate(64 * 4)
        pixels.forEach { buf.putInt(it) }
        transaction {
            PlayerFacesTable.upsert {
                it[PlayerFacesTable.uuid] = uuid.toKotlinUuid()
                it[PlayerFacesTable.pixels] = buf.array()
                it[fetchedAt] = now
            }
        }
        Unit
    }

    suspend fun loadSettings(player: UUID): PlayerSettings = withContext(Dispatchers.IO) {
        transaction {
            PlayerSettings(
                PlayerSettingTable.selectAll().where { PlayerSettingTable.player eq player.toKotlinUuid() }
                    .associate { it[PlayerSettingTable.key] to it[PlayerSettingTable.value] }
            )
        }
    }

    /** Replaces all stored overrides of [player] with [settings]. */
    suspend fun saveSettings(player: UUID, settings: PlayerSettings) = withContext(Dispatchers.IO) {
        transaction {
            PlayerSettingTable.deleteWhere { PlayerSettingTable.player eq player.toKotlinUuid() }
            for ((key, value) in settings.overrides) {
                PlayerSettingTable.insert {
                    it[PlayerSettingTable.player] = player.toKotlinUuid()
                    it[PlayerSettingTable.key] = key
                    it[PlayerSettingTable.value] = value
                }
            }
        }
        Unit
    }
}
