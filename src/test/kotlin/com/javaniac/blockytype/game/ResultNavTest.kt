package com.javaniac.blockytype.game

import com.javaniac.blockytype.menu.Nav
import kotlin.test.Test
import kotlin.test.assertEquals

class ResultNavTest {
    @Test
    fun `only A and D scroll the result`() {
        assertEquals(-1, TestSession.resultStep(Nav.LEFT))
        assertEquals(1, TestSession.resultStep(Nav.RIGHT))
        assertEquals(0, TestSession.resultStep(Nav.UP))
        assertEquals(0, TestSession.resultStep(Nav.DOWN))
        assertEquals(0, TestSession.resultStep(Nav.SELECT))
    }
}
