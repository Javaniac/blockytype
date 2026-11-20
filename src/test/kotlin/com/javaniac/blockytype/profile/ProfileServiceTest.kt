package com.javaniac.blockytype.profile

import com.javaniac.blockytype.db.Storage
import com.javaniac.blockytype.game.CharCounts
import com.javaniac.blockytype.game.SecondStat
import com.javaniac.blockytype.game.TestResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ProfileServiceTest {
    private fun result(wpm: Double) = TestResult(
        wpm = wpm, raw = wpm, accuracy = 98.0,
        counts = CharCounts(100, 2, 0, 0, 95, 102), consistency = 80.0, seconds = 20.0, perSecond = listOf(SecondStat(wpm, wpm, 0)),
    )

    private fun withDb(block: suspend () -> Unit) {
        val file = Files.createTempFile("blockytype-profile", ".db")
        try {
            Storage.init(file.toString())
            runBlocking { block() }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `names are checked before the database is asked`() = withDb {
        assertIs<Lookup.Invalid>(ProfileService.lookup("a b"))
        assertIs<Lookup.Invalid>(ProfileService.lookup("ab"))
        assertIs<Lookup.Invalid>(ProfileService.lookup("x".repeat(17)))
        assertIs<Lookup.Invalid>(ProfileService.lookup("../etc"))
        assertIs<Lookup.Invalid>(ProfileService.lookup(""))
    }

    @Test
    fun `a player that never joined is not found`() = withDb {
        assertIs<Lookup.Unknown>(ProfileService.lookup("Nobody"))
        val id = UUID.randomUUID()
        Storage.touchPlayer(id, "Alice")
        val found = ProfileService.lookup("alice")
        assertIs<Lookup.Found>(found)
        assertEquals(id, found.player.uuid)
        assertIs<Lookup.Found>(ProfileService.lookup("  Alice  ")) // typing slack
    }

    @Test
    fun `a profile has the stats, the face and caches the download`() = withDb {
        val id = UUID.randomUUID()
        Storage.touchPlayer(id, "Alice", now = 5_000)
        Storage.saveResult(id, "words 25 english", result(120.0))
        Storage.saveResult(id, "time 30 english", result(90.0))
        val known = Storage.playerByUuid(id)!!
        val face = IntArray(64) { 0x112233 }
        var downloads = 0
        val fetch = { _: UUID, _: String? -> downloads++; face }

        val p = ProfileService.load(known, textureValue = null, fetch = fetch)
        assertEquals("Alice", p.name)
        assertEquals(5_000L, p.joined)
        assertEquals(2, p.completed)
        assertEquals(120.0, p.bests["words 25"]!!.wpm)
        assertContentEquals(face, p.face)
        assertEquals(1, downloads)

        val again = ProfileService.load(known, textureValue = null, fetch = fetch)
        assertContentEquals(face, again.face)
        assertEquals(1, downloads, "the second profile uses the stored face")
    }

    @Test
    fun `a failed download shows the placeholder and is tried again next time`() = withDb {
        val id = UUID.randomUUID()
        Storage.touchPlayer(id, "Bob")
        val known = Storage.playerByUuid(id)!!
        var calls = 0
        val p = ProfileService.load(known, null) { _, _ -> calls++; null }
        assertContentEquals(SkinFace.PLACEHOLDER, p.face)
        ProfileService.load(known, null) { _, _ -> calls++; null }
        assertEquals(2, calls)
        assertNotNull(Storage.playerByUuid(id))
    }
}
