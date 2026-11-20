package com.javaniac.blockytype.settings

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerSettingsTest {
    private val original = ServerSettings.file

    @BeforeTest
    fun useTempFile() {
        ServerSettings.file = SettingsFile(Files.createTempDirectory("ss").resolve("blockytype.properties"), ServerSettingSpecs.all())
        ServerSettings.load()
    }

    @AfterTest
    fun restore() {
        ServerSettings.file = original
    }

    @Test
    fun `a server default is written to the file with its comment`() {
        ServerDefaults.set("font", "fira-code")
        val text = ServerSettings.file.path.readText()
        assertTrue("font=fira-code" in text)
        assertTrue(text.lines().any { it.startsWith("# Server default:") && "font" in it.lowercase() })
        assertEquals("fira-code", ServerDefaults["font"])
    }

    @Test
    fun `clearing a server default writes the catalog default back`() {
        ServerDefaults.set("input-box", "hide")
        ServerDefaults.clear("input-box")
        assertTrue("input-box=show" in ServerSettings.file.path.readText())
        assertEquals("show", ServerDefaults["input-box"])
    }

    @Test
    fun `a network setting is saved and pending until restart`() {
        ServerSettings.setNetwork("resource-pack-ip", "127.0.0.1")
        assertTrue("resource-pack-ip=127.0.0.1" in ServerSettings.file.path.readText())
        assertEquals("127.0.0.1", com.javaniac.blockytype.NetworkPanel.snapshot()["resource-pack-ip"])
    }

    @Test
    fun `hand edits are picked up by load`() {
        val path = ServerSettings.file.path
        path.toFile().writeText(path.readText().replace("anti-aliasing=on", "anti-aliasing=off"))
        ServerSettings.load()
        assertEquals("off", ServerDefaults["anti-aliasing"])
    }

    @Test
    fun `an invalid value is rejected and nothing changes`() {
        ServerDefaults.set("animation", "sideways")
        assertEquals("on", ServerDefaults["animation"])
        assertTrue("animation=on" in ServerSettings.file.path.readText())
    }
}
