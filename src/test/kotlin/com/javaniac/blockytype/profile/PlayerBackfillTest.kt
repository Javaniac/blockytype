package com.javaniac.blockytype.profile

import com.javaniac.blockytype.commands.Profile
import com.javaniac.blockytype.db.PlayerSettings
import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.game.CharCounts
import com.javaniac.blockytype.game.SecondStat
import com.javaniac.blockytype.game.TestResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerBackfillTest {
    private fun result() = TestResult(
        wpm = 80.0, raw = 82.0, accuracy = 97.0, counts = CharCounts(100, 2, 0, 0, 95, 102),
        consistency = 80.0, seconds = 20.0, perSecond = listOf(SecondStat(80.0, 80.0, 0)),
    )

    @Test
    fun `players with data but no row are added with the name Mojang knows`() {
        val file = Files.createTempFile("blockytype-backfill", ".db")
        try {
            Storage.init(file.toString())
            runBlocking {
                val old = UUID.randomUUID()
                val ghost = UUID.randomUUID()
                val admin = UUID.randomUUID()
                Storage.saveResult(old, "words 10 english", result())
                Storage.saveSettings(ghost, PlayerSettings(mapOf("theme" to "nord")))
                Storage.saveSettings(admin, PlayerSettings(mapOf("theme" to "nord")))
                Storage.addAdmin(admin, "TheBoss")
                val asked = mutableListOf<UUID>()
                val added = PlayerBackfill.run { id -> asked += id; if (id == old) Profile(id, "OldTimer") else null }
                assertEquals(2, added)
                assertEquals("OldTimer", Storage.playerByName("oldtimer")?.name)
                assertEquals("TheBoss", Storage.playerByUuid(admin)?.name, "admins are known by name, no lookup needed")
                assertNull(Storage.playerByUuid(ghost), "a player Mojang does not know is skipped")
                assertEquals(setOf(old, ghost), asked.toSet())
                // joined = their first saved test
                val first = Storage.profileResults(old).minOf { it.at }
                assertEquals(first, Storage.playerByUuid(old)!!.firstSeen)
                // a second run only asks about the ones still missing
                asked.clear()
                assertEquals(0, PlayerBackfill.run { id -> asked += id; null })
                assertEquals(listOf(ghost), asked)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
