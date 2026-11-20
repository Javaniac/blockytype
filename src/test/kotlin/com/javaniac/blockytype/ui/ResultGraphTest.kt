package com.javaniac.blockytype.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultGraphTest {
    @Test
    fun `the plot is short enough for the heatmap below it`() {
        assertTrue(ResultGraph.H in 40..50, "${ResultGraph.H}")
        assertTrue(ResultGraph.H <= ResultGraph.ATLAS)
    }

    @Test
    fun `the marker is a 3 by 3 block centered on the line`() {
        val values = listOf(10.0, 55.0, 90.0, 40.0)
        val dots = ResultGraph.markerDots(2, values.size, values[2], 100)
        assertEquals(9, dots.size)
        val x = ResultGraph.xOf(2, values.size)
        val h = ResultGraph.heightOf(values[2], 100)
        assertEquals((x - 1..x + 1).toSet(), dots.map { it.first }.toSet())
        assertEquals((h - 1..h + 1).toSet(), dots.map { it.second }.toSet())
        // the line passes through the center of the marker
        assertTrue((x to h) in ResultGraph.seriesDots(values, 100))
    }

    @Test
    fun `the marker stays inside the plot`() {
        for (v in listOf(0.0, 100.0)) {
            for (i in listOf(0, 9)) {
                for ((x, h) in ResultGraph.markerDots(i, 10, v, 100)) {
                    assertTrue(x in 0 until ResultGraph.W && h in 0 until ResultGraph.H, "($x,$h)")
                }
            }
        }
    }

    @Test
    fun `marker strings return to the origin`() {
        val s = ResultGraph.marker(1, 3, 50.0, 100)
        assertEquals(0, ResultGraph.advanceOf(s))
        assertEquals(9, s.count { it.code in ResultGraph.DOT_BASE until ResultGraph.DOT_BASE + ResultGraph.ATLAS })
    }
}
