package com.javaniac.blockytype.pack

import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class PackUrlTest {
    private val config = PackServerConfig(host = "194.15.36.161", port = 25540)
    private val hash = "0fa84fe4306f9a88d0850a511588e388ddc45fa7"

    @Test
    fun `remote players download from the configured host`() {
        val remote = InetSocketAddress.createUnresolved("203.0.113.5", 50000)
        assertEquals("http://194.15.36.161:25540/pack/$hash.zip", config.url(hash, remote))
        assertEquals("http://194.15.36.161:25540/pack/$hash.zip", config.url(hash, null))
    }

    @Test
    fun `players on this machine download over loopback`() {
        // Hairpin NAT: a client on the server's own PC cannot reach the public IP.
        val v4 = InetSocketAddress("127.0.0.1", 50000)
        val v6 = InetSocketAddress("::1", 50000)
        assertEquals("http://127.0.0.1:25540/pack/$hash.zip", config.url(hash, v4))
        assertEquals("http://127.0.0.1:25540/pack/$hash.zip", config.url(hash, v6))
    }
}
