package com.javaniac.blockytype.db

import com.javaniac.blockytype.game.CharCounts
import com.javaniac.blockytype.game.SecondStat
import com.javaniac.blockytype.game.TestResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayersStorageTest {
    private fun result(wpm: Double) = TestResult(
        wpm = wpm, raw = wpm + 3, accuracy = 97.5,
        counts = CharCounts(correct = 100, incorrect = 2, extra = 1, missed = 0, correctWordChars = 95, rawChars = 103),
        consistency = 80.0, seconds = 12.5, perSecond = listOf(SecondStat(wpm, wpm, 0)),
    )

    private fun withDb(block: suspend () -> Unit) {
        val file = Files.createTempFile("blockytype-players", ".db")
        try {
            Storage.init(file.toString())
            runBlocking { block() }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `a player is known after touch, names are found without case`() = withDb {
        val alice = UUID.randomUUID()
        assertNull(Storage.playerByName("Alice"))
        Storage.touchPlayer(alice, "Alice", now = 1000)
        val found = Storage.playerByName("aLiCe")
        assertNotNull(found)
        assertEquals(alice, found.uuid)
        assertEquals("Alice", found.name)
        assertEquals(1000L, found.firstSeen)
        assertEquals(alice, Storage.playerByUuid(alice)?.uuid)
        assertNull(Storage.playerByName("Alic"))
        assertNull(Storage.playerByName("Alice "))
    }

    @Test
    fun `touching again keeps the first seen time and takes the new name`() = withDb {
        val id = UUID.randomUUID()
        Storage.touchPlayer(id, "Old", now = 1000)
        Storage.touchPlayer(id, "New", now = 5000)
        val p = Storage.playerByUuid(id)!!
        assertEquals("New", p.name)
        assertEquals(1000L, p.firstSeen)
        assertEquals(5000L, p.lastSeen)
        assertNull(Storage.playerByName("Old"))
        assertEquals(id, Storage.playerByName("new")?.uuid)
    }

    @Test
    fun `a name taken over by another player belongs to the newer one`() = withDb {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        Storage.touchPlayer(a, "Steve", now = 1000)
        Storage.touchPlayer(b, "Steve", now = 2000)
        assertEquals(b, Storage.playerByName("steve")?.uuid)
    }

    @Test
    fun `results of a player come back with their data`() = withDb {
        val id = UUID.randomUUID()
        val other = UUID.randomUUID()
        Storage.saveResult(id, "words 25 english", result(100.0))
        Storage.saveResult(id, "time 30 english", result(90.0))
        Storage.saveResult(other, "words 25 english", result(200.0))
        val rows = Storage.profileResults(id)
        assertEquals(2, rows.size)
        assertEquals(setOf("words 25 english", "time 30 english"), rows.map { it.mode }.toSet())
        val r = rows.first { it.mode == "words 25 english" }
        assertEquals(100.0, r.wpm)
        assertEquals(103.0, r.raw)
        assertEquals(97.5, r.accuracy)
        assertEquals(80.0, r.consistency)
        assertEquals(12.5, r.seconds)
        assertTrue(r.at > 0)
        assertEquals(emptyList(), Storage.profileResults(UUID.randomUUID()))
    }

    @Test
    fun `players that only have data but no row are listed for the backfill`() = withDb {
        val withRow = UUID.randomUUID()
        val noRow = UUID.randomUUID()
        val settingsOnly = UUID.randomUUID()
        Storage.touchPlayer(withRow, "Known", now = 1)
        Storage.saveResult(withRow, "words 10 english", result(50.0))
        Storage.saveResult(noRow, "words 10 english", result(60.0))
        Storage.saveSettings(settingsOnly, PlayerSettings(mapOf("theme" to "nord")))
        assertEquals(setOf(noRow, settingsOnly), Storage.playersWithoutRow().toSet())
        Storage.touchPlayer(noRow, "Back", now = 2, firstSeen = 99)
        assertEquals(99L, Storage.playerByUuid(noRow)!!.firstSeen)
        assertEquals(setOf(settingsOnly), Storage.playersWithoutRow().toSet())
    }

    @Test
    fun `faces are stored and expire`() = withDb {
        val id = UUID.randomUUID()
        assertNull(Storage.face(id, now = 10, maxAgeMillis = 100))
        val pixels = IntArray(64) { it * 0x010203 and 0xFFFFFF }
        Storage.saveFace(id, pixels, now = 10)
        assertContentEquals(pixels, Storage.face(id, now = 50, maxAgeMillis = 100))
        assertNull(Storage.face(id, now = 500, maxAgeMillis = 100)) // too old
        Storage.saveFace(id, IntArray(64) { 0x112233 }, now = 600)
        assertEquals(0x112233, Storage.face(id, now = 600, maxAgeMillis = 100)!![63])
    }
}
