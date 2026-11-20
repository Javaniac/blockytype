package com.javaniac.blockytype.menu

import kotlin.test.Test
import kotlin.test.assertEquals

class MenuInputTest {
    @Test
    fun `scroll direction from hotbar slot change`() {
        assertEquals(1, MenuInput.scrollDirection(4, 5))
        assertEquals(-1, MenuInput.scrollDirection(4, 3))
        assertEquals(0, MenuInput.scrollDirection(4, 4))
    }

    @Test
    fun `scroll direction wraps around the hotbar`() {
        assertEquals(1, MenuInput.scrollDirection(8, 0))
        assertEquals(-1, MenuInput.scrollDirection(0, 8))
    }

    @Test
    fun `number keys give a direction too`() {
        assertEquals(1, MenuInput.scrollDirection(4, 7))
        assertEquals(-1, MenuInput.scrollDirection(4, 0))
    }

    private val none = InputKeys()

    @Test
    fun `a pressed key gives one event until it is released`() {
        assertEquals(listOf(Nav.UP), MenuInput.navPresses(none, InputKeys(forward = true)))
        assertEquals(emptyList(), MenuInput.navPresses(InputKeys(forward = true), InputKeys(forward = true)))
        assertEquals(emptyList(), MenuInput.navPresses(InputKeys(forward = true), none))
        assertEquals(listOf(Nav.UP), MenuInput.navPresses(none, InputKeys(forward = true)))
    }

    @Test
    fun `wasd and space map to navigation`() {
        assertEquals(listOf(Nav.UP), MenuInput.navPresses(none, InputKeys(forward = true)))
        assertEquals(listOf(Nav.DOWN), MenuInput.navPresses(none, InputKeys(backward = true)))
        assertEquals(listOf(Nav.LEFT), MenuInput.navPresses(none, InputKeys(left = true)))
        assertEquals(listOf(Nav.RIGHT), MenuInput.navPresses(none, InputKeys(right = true)))
        assertEquals(listOf(Nav.SELECT), MenuInput.navPresses(none, InputKeys(jump = true)))
    }

    private val ms = 1_000_000L

    private fun repeater() = KeyRepeater(delayNanos = 300 * ms, intervalNanos = 100 * ms)

    @Test
    fun `a held key repeats after the delay at the interval`() {
        val r = repeater()
        assertEquals(listOf(Nav.DOWN), r.update(InputKeys(backward = true), 0))
        assertEquals(emptyList(), r.update(InputKeys(backward = true), 299 * ms))
        assertEquals(listOf(Nav.DOWN), r.update(InputKeys(backward = true), 300 * ms))
        assertEquals(emptyList(), r.update(InputKeys(backward = true), 350 * ms))
        assertEquals(listOf(Nav.DOWN), r.update(InputKeys(backward = true), 400 * ms))
    }

    @Test
    fun `releasing the key stops the repeat`() {
        val r = repeater()
        r.update(InputKeys(forward = true), 0)
        assertEquals(emptyList(), r.update(InputKeys(), 100 * ms))
        assertEquals(emptyList(), r.update(InputKeys(), 1000 * ms))
    }

    @Test
    fun `a late tick catches up with at most one step`() {
        val r = repeater()
        r.update(InputKeys(forward = true), 0)
        assertEquals(listOf(Nav.UP), r.update(InputKeys(forward = true), 5000 * ms))
        assertEquals(emptyList(), r.update(InputKeys(forward = true), 5050 * ms))
    }

    @Test
    fun `space never repeats`() {
        val r = repeater()
        assertEquals(listOf(Nav.SELECT), r.update(InputKeys(jump = true), 0))
        assertEquals(emptyList(), r.update(InputKeys(jump = true), 2000 * ms))
    }

    @Test
    fun `pressing another direction takes over the repeat`() {
        val r = repeater()
        r.update(InputKeys(forward = true), 0)
        assertEquals(listOf(Nav.RIGHT), r.update(InputKeys(forward = true, right = true), 100 * ms))
        assertEquals(emptyList(), r.update(InputKeys(forward = true, right = true), 399 * ms))
        assertEquals(listOf(Nav.RIGHT), r.update(InputKeys(forward = true, right = true), 400 * ms))
    }

    @Test
    fun `releasing the newer key resumes nothing until pressed again`() {
        val r = repeater()
        r.update(InputKeys(forward = true), 0)
        r.update(InputKeys(forward = true, right = true), 100 * ms)
        assertEquals(emptyList(), r.update(InputKeys(forward = true), 1000 * ms))
    }

    @Test
    fun `holding one key while pressing another only reports the new one`() {
        assertEquals(listOf(Nav.RIGHT), MenuInput.navPresses(InputKeys(forward = true), InputKeys(forward = true, right = true)))
    }
}
