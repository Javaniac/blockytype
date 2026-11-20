package com.javaniac.blockytype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkSettingsTest {
    private val running = NetworkConfig(
        serverIp = Resolved("0.0.0.0", Source.DEFAULT),
        serverPort = Resolved(25565, Source.DEFAULT),
        resourcePackIp = Resolved("localhost", Source.DEFAULT),
        resourcePackPort = Resolved(25500, Source.DEFAULT),
    )

    @Test
    fun `ports must be 1 to 65535`() {
        assertEquals("25570", NetworkSettings.validate("server-port", " 25570 "))
        assertNull(NetworkSettings.validate("server-port", "0"))
        assertNull(NetworkSettings.validate("resource-pack-port", "65536"))
        assertNull(NetworkSettings.validate("resource-pack-port", "abc"))
    }

    @Test
    fun `hosts must be a single token`() {
        assertEquals("play.example.com", NetworkSettings.validate("resource-pack-ip", " play.example.com "))
        assertEquals("10.0.0.5", NetworkSettings.validate("server-ip", "10.0.0.5"))
        assertNull(NetworkSettings.validate("server-ip", ""))
        assertNull(NetworkSettings.validate("server-ip", "a b"))
        assertNull(NetworkSettings.validate("server-ip", "evil\nline"))
        assertNull(NetworkSettings.validate("server-ip", "x".repeat(254)))
    }

    @Test
    fun `unknown keys are rejected`() {
        assertNull(NetworkSettings.validate("font", "x"))
        assertNull(NetworkSettings.validate("../../x", "1"))
    }

    @Test
    fun `fields show the running values with their source`() {
        val f = NetworkSettings.fields(running, panel = emptyMap())
        assertEquals(listOf("server-ip", "server-port", "resource-pack-ip", "resource-pack-port"), f.map { it.key })
        assertEquals("0.0.0.0", f[0].value)
        assertEquals(Source.DEFAULT, f[0].source)
        assertTrue(f.none { it.pendingRestart })
    }

    @Test
    fun `a saved panel value is shown and marked as pending until restart`() {
        val f = NetworkSettings.fields(running, panel = mapOf("server-port" to "25570"))
        val port = f.single { it.key == "server-port" }
        assertEquals("25570", port.value)
        assertEquals(Source.FILE, port.source)
        assertTrue(port.pendingRestart)
    }

    @Test
    fun `a panel value equal to the running one is not pending`() {
        val f = NetworkSettings.fields(running, panel = mapOf("server-port" to "25565"))
        assertEquals(false, f.single { it.key == "server-port" }.pendingRestart)
    }

    @Test
    fun `invalid saved panel values are ignored`() {
        val f = NetworkSettings.fields(running, panel = mapOf("server-port" to "99999", "server-ip" to ""))
        assertEquals("25565", f.single { it.key == "server-port" }.value)
        assertEquals("0.0.0.0", f.single { it.key == "server-ip" }.value)
    }
}
