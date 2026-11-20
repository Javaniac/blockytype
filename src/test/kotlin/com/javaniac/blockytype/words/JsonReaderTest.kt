package com.javaniac.blockytype.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonReaderTest {
    @Test
    fun `parses objects arrays and scalars`() {
        val v = JsonReader.parse("""{"a":[1,2.5,"x\nä\"",true,null],"b":{"c":false}}""") as Map<*, *>
        assertEquals(listOf(1L, 2.5, "x\nä\"", true, null), v["a"])
        assertEquals(mapOf("c" to false), v["b"])
    }

    @Test
    fun `skips whitespace and keeps non ascii`() {
        val v = JsonReader.parse(" { \"words\" : [ \"ça\" , \"ñu\" ] } ") as Map<*, *>
        assertEquals(listOf("ça", "ñu"), v["words"])
    }

    @Test
    fun `rejects garbage`() {
        assertFailsWith<IllegalArgumentException> { JsonReader.parse("{\"a\":") }
        assertFailsWith<IllegalArgumentException> { JsonReader.parse("[1,]x") }
    }
}
