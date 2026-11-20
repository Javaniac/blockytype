package com.javaniac.blockytype.sound

import com.javaniac.blockytype.pack.PackBuilder
import com.javaniac.blockytype.settings.SettingsCatalog
import com.javaniac.blockytype.words.JsonReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundsTest {
    @Test
    fun `the index lists every ogg of the sound pack`() {
        val files = Sounds.files()
        assertTrue(files.size > 200, "only ${files.size} files")
        for (f in files) {
            assertTrue(f.endsWith(".ogg") && f == f.lowercase() && !f.contains(' '), f)
            val bytes = Sounds.bytes(f)
            assertEquals("OggS", String(bytes.copyOf(4)), "$f is not an ogg file")
        }
    }

    @Test
    fun `every click option maps to a sound set`() {
        val options = SettingsCatalog.byId("play-sound-on-click")!!.options.values().filter { it != "off" }
        assertEquals(26, options.size)
        for (o in options) assertNotNull(Sounds.clickEvent(o, 'a'), o)
        assertNull(Sounds.clickEvent("off", 'a'))
        assertNull(Sounds.clickEvent("bogus", 'a'))
    }

    @Test
    fun `sample sets let the client pick a variant, oscillators play one note per key`() {
        assertEquals("blockytype:click4", Sounds.clickEvent("nk creams", 'a'))
        assertEquals("blockytype:click4", Sounds.clickEvent("nk creams", 'z'))
        assertEquals("blockytype:click12", Sounds.clickEvent("pentatonic", 'q'))
        val a = Sounds.clickEvent("sine", 'a')!!
        assertTrue(a.startsWith("blockytype:click8_"), a)
        assertEquals(a, Sounds.clickEvent("sine", 'A'), "case does not change the note")
        assertTrue(Sounds.clickEvent("sine", 'a') != Sounds.clickEvent("sine", 'b'))
        assertTrue(Sounds.clickEvent("square", 'a')!!.startsWith("blockytype:click10_"))
        val notes = ('a'..'z').map { Sounds.clickEvent("triangle", it) }.toSet()
        assertTrue(notes.size > 5 && notes.size <= 14, "${notes.size}")
    }

    @Test
    fun `error and time warning options`() {
        assertEquals("blockytype:error1", Sounds.errorEvent("damage"))
        assertEquals("blockytype:error4", Sounds.errorEvent("missed punch"))
        assertNull(Sounds.errorEvent("off"))
        for (o in SettingsCatalog.byId("play-sound-on-error")!!.options.values().filter { it != "off" }) assertNotNull(Sounds.errorEvent(o), o)
        assertEquals(listOf(null, 1, 3, 5, 10), SettingsCatalog.byId("play-time-warning")!!.options.values().map(Sounds::warningSeconds))
    }

    @Test
    fun `volume follows the setting`() {
        assertEquals(0f, Sounds.volume("0"))
        assertEquals(0.5f, Sounds.volume("0.5"))
        assertEquals(1f, Sounds.volume("1"))
        assertEquals(0.5f, Sounds.volume("bogus"))
        for (v in SettingsCatalog.byId("sound-volume")!!.options.values()) assertTrue(Sounds.volume(v) in 0f..1f)
    }

    @Test
    fun `sounds json lists real files for every event`() {
        val json = JsonReader.parse(Sounds.soundsJson()) as Map<*, *>
        val files = Sounds.files().map { it.removeSuffix(".ogg") }.toSet()
        assertTrue(json.isNotEmpty())
        for ((event, def) in json) {
            val sounds = (def as Map<*, *>)["sounds"] as List<*>
            assertTrue(sounds.isNotEmpty(), "$event")
            for (s in sounds) {
                val name = (s as? String) ?: ((s as Map<*, *>)["name"] as String)
                assertTrue(name.startsWith("blockytype:"), name)
                assertTrue(name.removePrefix("blockytype:") in files, "$event -> $name")
            }
        }
        for (set in listOf(1, 4, 12, 13, 14, 26)) assertTrue("click$set" in json, "click$set")
        for (e in listOf("click8_0", "click11_13", "error1", "error4", "time_warning")) assertTrue(e in json, e)
        assertEquals(6, ((json["click4"] as Map<*, *>)["sounds"] as List<*>).size)
        assertEquals(1, ((json["click8_3"] as Map<*, *>)["sounds"] as List<*>).size)
    }

    @Test
    fun `every event an option can play exists in sounds json`() {
        val json = JsonReader.parse(Sounds.soundsJson()) as Map<*, *>
        fun known(event: String?) = event != null && event.removePrefix("blockytype:") in json
        for (o in SettingsCatalog.byId("play-sound-on-click")!!.options.values().filter { it != "off" }) {
            for (c in ('a'..'z') + listOf(' ', '.', '5')) assertTrue(known(Sounds.clickEvent(o, c)), "$o $c")
        }
        for (o in SettingsCatalog.byId("play-sound-on-error")!!.options.values().filter { it != "off" }) assertTrue(known(Sounds.errorEvent(o)), o)
        assertTrue(known(Sounds.TIME_WARNING))
    }

    @Test
    fun `the pack carries the sounds`() {
        val files = PackBuilder.build()
        assertTrue("assets/blockytype/sounds.json" in files)
        assertTrue("assets/blockytype/sounds/click1/1.ogg" in files)
        assertTrue("assets/blockytype/sounds/time_warning.ogg" in files)
        for (f in Sounds.files()) assertTrue("assets/blockytype/sounds/$f" in files, f)
        for (path in files.keys.filter { it.startsWith("assets/") }) assertEquals(path, path.lowercase(), "asset paths must be lowercase")
    }
}

class TimeWarningTest {
    @Test
    fun `fires once when the remaining seconds reach the setting`() {
        val w = TimeWarning(3)
        assertTrue(!w.check(10))
        assertTrue(!w.check(4))
        assertTrue(w.check(3))
        assertTrue(!w.check(3)) // not again
        assertTrue(!w.check(2))
    }

    @Test
    fun `a jump over the second still fires once`() {
        val w = TimeWarning(5)
        assertTrue(!w.check(8))
        assertTrue(w.check(4)) // the check at 5 was skipped
        assertTrue(!w.check(3))
    }

    @Test
    fun `off never fires and a reset arms it again`() {
        val off = TimeWarning(null)
        for (s in 10 downTo 0) assertTrue(!off.check(s))
        val w = TimeWarning(1)
        assertTrue(w.check(1))
        w.reset()
        assertTrue(!w.check(5))
        assertTrue(w.check(1))
    }
}
