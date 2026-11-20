package com.javaniac.blockytype.commands

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GrantAdminTest {
    @Test
    fun `parses a mojang profile response`() {
        val profile = MojangProfiles.parse("""{"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch"}""")
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), profile?.uuid)
        assertEquals("Notch", profile?.name)
    }

    @Test
    fun `garbage and empty responses give null`() {
        assertNull(MojangProfiles.parse(""))
        assertNull(MojangProfiles.parse("{}"))
        assertNull(MojangProfiles.parse("""{"id":"zz","name":"x"}"""))
    }

    @Test
    fun `valid usernames only`() {
        assertEquals(true, MojangProfiles.isValidName("Javaniac"))
        assertEquals(true, MojangProfiles.isValidName("a_b_1"))
        assertEquals(false, MojangProfiles.isValidName("ab"))
        assertEquals(false, MojangProfiles.isValidName("way_too_long_for_minecraft"))
        assertEquals(false, MojangProfiles.isValidName("bad name"))
        assertEquals(false, MojangProfiles.isValidName("../x"))
    }
}
