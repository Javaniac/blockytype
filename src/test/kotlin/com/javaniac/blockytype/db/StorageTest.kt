package com.javaniac.blockytype.db

import com.javaniac.blockytype.game.CharCounts
import com.javaniac.blockytype.game.SecondStat
import com.javaniac.blockytype.game.TestResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageTest {
    private fun result(wpm: Double) = TestResult(
        wpm = wpm, raw = wpm + 3, accuracy = 97.5,
        counts = CharCounts(correct = 100, incorrect = 2, extra = 1, missed = 0, correctWordChars = 95, rawChars = 103),
        consistency = 80.0, seconds = 12.5, perSecond = listOf(SecondStat(wpm, wpm, 0)),
    )

    @Test
    fun `legacy theme column is migrated once`() = runBlocking {
        val file = Files.createTempFile("blockytype-test", ".db")
        try {
            Storage.init(file.toString())
            val alice = UUID.randomUUID()
            val bob = UUID.randomUUID()
            Storage.insertLegacyTheme(alice, "nord")
            Storage.insertLegacyTheme(bob, PlayerSettings.DEFAULT_THEME)

            Storage.migrateLegacySettings()
            assertEquals(mapOf("theme" to "nord"), Storage.loadSettings(alice).overrides)
            assertEquals(emptyMap(), Storage.loadSettings(bob).overrides)

            Storage.saveSettings(alice, PlayerSettings()) // the player reset everything
            Storage.migrateLegacySettings() // must not bring the old theme back
            assertEquals(emptyMap(), Storage.loadSettings(alice).overrides)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `admins are stored and removed`() = runBlocking {
        val file = Files.createTempFile("blockytype-test", ".db")
        try {
            Storage.init(file.toString())
            val alice = UUID.randomUUID()
            val bob = UUID.randomUUID()

            assertEquals(emptyList(), Storage.admins())
            Storage.addAdmin(alice, "Alice")
            Storage.addAdmin(bob, "Bob")
            Storage.addAdmin(alice, "AliceRenamed") // upsert keeps one row
            assertEquals(setOf(Admin(alice, "AliceRenamed"), Admin(bob, "Bob")), Storage.admins().toSet())
            assertEquals(true, Storage.removeAdmin(bob))
            assertEquals(false, Storage.removeAdmin(bob))
            assertEquals(listOf(Admin(alice, "AliceRenamed")), Storage.admins())
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `server settings are stored by key`() = runBlocking {
        val file = Files.createTempFile("blockytype-test", ".db")
        try {
            Storage.init(file.toString())
            assertNull(Storage.serverSetting("font"))
            Storage.setServerSetting("font", "jetbrains-mono")
            Storage.setServerSetting("font", "fira-code") // upsert replaces
            Storage.setServerSetting("anti-aliasing", "off")
            assertEquals("fira-code", Storage.serverSetting("font"))
            assertEquals(mapOf("font" to "fira-code", "anti-aliasing" to "off"), Storage.serverSettings())
            Storage.clearServerSetting("font")
            assertNull(Storage.serverSetting("font"))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `settings and results are stored per player`() = runBlocking {
        val file = Files.createTempFile("blockytype-test", ".db")
        try {
            Storage.init(file.toString())
            val alice = UUID.randomUUID()
            val bob = UUID.randomUUID()

            assertEquals(PlayerSettings(), Storage.loadSettings(alice)) // defaults for unknown players
            Storage.saveSettings(alice, PlayerSettings().with("theme", "serika"))
            Storage.saveSettings(alice, PlayerSettings().with("theme", "nord").with("font", "fira-code")) // replaces all
            assertEquals("nord", Storage.loadSettings(alice).theme)
            assertEquals(mapOf("theme" to "nord", "font" to "fira-code"), Storage.loadSettings(alice).overrides)
            Storage.saveSettings(alice, Storage.loadSettings(alice).without("font")) // back to the server default
            assertEquals(mapOf("theme" to "nord"), Storage.loadSettings(alice).overrides)
            assertEquals(PlayerSettings.DEFAULT_THEME, Storage.loadSettings(bob).theme)

            assertNull(Storage.bestWpm(alice, "words 29 english"))
            Storage.saveResult(alice, "words 29 english", result(80.0))
            Storage.saveResult(alice, "words 29 english", result(95.5))
            Storage.saveResult(alice, "words 10 english", result(200.0))
            Storage.saveResult(bob, "words 29 english", result(120.0))
            assertEquals(95.5, Storage.bestWpm(alice, "words 29 english"))
            assertEquals(120.0, Storage.bestWpm(bob, "words 29 english"))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `recent results come newest first and feed the averages`() = runBlocking {
        val file = Files.createTempFile("blockytype-test", ".db")
        try {
            Storage.init(file.toString())
            val alice = UUID.randomUUID()
            val bob = UUID.randomUUID()
            assertEquals(emptyList(), Storage.recentResults(alice, 10))
            for (w in listOf(60.0, 80.0, 100.0)) Storage.saveResult(alice, "words 25 english", result(w))
            Storage.saveResult(alice, "time 30 english", result(40.0))
            Storage.saveResult(bob, "words 25 english", result(200.0))

            val recent = Storage.recentResults(alice, 3)
            assertEquals(listOf(40.0, 100.0, 80.0), recent.map { it.wpm })
            assertEquals("time 30 english", recent.first().mode)

            val stats = PlayerStats.of(Storage.recentResults(alice, 10), "words 25 english", Storage.bestWpm(alice, "words 25 english"))
            assertEquals(100.0, stats.best)
            assertEquals(100.0, stats.last) // the newest result of that mode
            assertEquals(80.0, stats.average) // 60, 80, 100
            val all = PlayerStats.of(Storage.recentResults(alice, 10), null, null)
            assertEquals(70.0, all.average) // every mode: 60, 80, 100, 40
            assertEquals(97.5, all.accuracy)
            assertNull(PlayerStats.of(emptyList(), "x", null).average)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
