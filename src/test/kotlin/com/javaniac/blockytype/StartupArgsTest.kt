package com.javaniac.blockytype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupArgsTest {
    @Test
    fun `file beats default`() {
        assertEquals(Resolved("p", Source.FILE), resolve("p", "d"))
        assertEquals(Resolved("d", Source.DEFAULT), resolve(null, "d"))
    }

    @Test
    fun `banner shows values and sources`() {
        val banner = StartupArgs.banner(
            NetworkConfig(
                serverIp = Resolved("0.0.0.0", Source.DEFAULT),
                serverPort = Resolved(25570, Source.FILE),
                resourcePackIp = Resolved("localhost", Source.FILE),
                resourcePackPort = Resolved(25500, Source.DEFAULT),
            )
        )
        assertTrue("0.0.0.0" in banner)
        assertTrue("25570" in banner)
        assertTrue("file" in banner)
        assertTrue("default" in banner)
    }
}
