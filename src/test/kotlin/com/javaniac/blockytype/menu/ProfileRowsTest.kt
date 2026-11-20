package com.javaniac.blockytype.menu

import com.javaniac.blockytype.profile.ProfileBuilder
import com.javaniac.blockytype.profile.ProfileData
import com.javaniac.blockytype.profile.ResultRow
import com.javaniac.blockytype.ui.MenuRow
import com.javaniac.blockytype.ui.Role
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRowsTest {
    private val day = 86_400_000L
    private val now = 1_700_000_000_000L
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val face = IntArray(64) { it }

    private fun row(mode: String, wpm: Double, at: Long, acc: Double = 98.0) = ResultRow(mode, wpm, wpm + 3, acc, 85.0, 20.0, at)

    private fun data(results: List<ResultRow>, name: String = "Javaniac") =
        ProfileBuilder.build(id, name, 1621900800000L, results, face)

    private fun lines(
        d: ProfileData, own: Boolean = true, page: Int = 0, editing: FieldEdit? = null,
    ) = ProfileRows.lines(ProfileView.Ready(d), own, page, now, { it.roundToInt().toString() }, { "${it.roundToInt()}%" }, editing)

    private fun MenuRow.text() = cells.joinToString("") { it.text }
    private fun MenuRow.actions() = cells.mapNotNull { it.action }

    private val sample = data(
        listOf(
            row("words 25 english", 120.0, now - 3 * day), row("words 25 english", 100.0, now - 2 * day),
            row("time 15 english", 90.0, now - day), row("time 60 english", 70.0, now - 3_600_000),
        ),
    )

    @Test
    fun `the head rows show the face, name, join date and level`() {
        val l = lines(sample)
        assertContentEquals(face, l[0].row.face!!.pixels)
        assertEquals(-150, l[0].row.face!!.x)
        assertTrue("Javaniac" in l[0].row.text())
        assertTrue("joined 25 May 2021" in l[1].row.text())
        assertTrue(l[2].row.text().startsWith("lvl "), l[2].row.text())
        assertTrue("/" in l[2].row.text(), "xp progress")
        assertNull(l[1].row.face)
    }

    @Test
    fun `the level bar is filled by the progress`() {
        val fills = lines(sample)[2].row.cells.filter { it.fill != null }
        assertTrue(fills.all { it.fill == Role.MAIN || it.fill == Role.SUB_ALT })
        assertEquals(20, fills.sumOf { it.text.length }, "the bar is 20 chars wide")
    }

    @Test
    fun `totals and the look up buttons`() {
        val own = lines(sample)[3]
        assertTrue("4 tests" in own.row.text(), own.row.text())
        assertTrue("00:01:20" in own.row.text(), own.row.text())
        assertEquals(listOf("lookup"), own.row.actions())
        val other = lines(sample, own = false)[3]
        assertEquals(listOf("lookup", "profile:self"), other.row.actions())
    }

    @Test
    fun `personal bests are two rows of four`() {
        val l = lines(sample)
        val time = l[4].row
        val words = l[5].row
        assertTrue(listOf("15s", "30s", "60s", "2m").all { it in time.text() }, time.text())
        assertTrue(listOf("10w", "25w", "50w", "100w").all { it in words.text() }, words.text())
        assertTrue("90" in time.text() && "98%" in time.text())
        assertTrue("120" in words.text())
        assertEquals(2, time.text().count { it == '-' }, "modes without a result show a dash: ${time.text()}")
        assertEquals(1 + 2 + 3, 6)
    }

    @Test
    fun `recent tests are newest first with their numbers`() {
        val l = lines(sample)
        assertEquals("recent tests", l[6].row.cells.first().text.trim())
        val recent = l.drop(7)
        assertEquals(4, recent.size)
        assertTrue("time 60" in recent[0].row.text() && "70" in recent[0].row.text() && "1h" in recent[0].row.text(), recent[0].row.text())
        assertTrue("words 25" in recent[3].row.text() && "3d" in recent[3].row.text(), recent[3].row.text())
        assertTrue("raw 123" in recent[3].row.text(), recent[3].row.text())
        assertTrue("85% cons" in recent[3].row.text(), recent[3].row.text())
    }

    @Test
    fun `never more rows than the menu has`() {
        val many = data((1..40).map { row("words 10 english", 50.0 + it, now - it * 1000L) })
        for (p in 0..7) assertTrue(lines(many, page = p).size <= 12, "page $p")
        assertTrue(lines(sample).size <= 12)
    }

    @Test
    fun `recent tests have pages of five`() {
        val many = data((1..12).map { row("words 10 english", 50.0 + it, now - it * 1000L) })
        val first = lines(many, page = 0)
        val pager = first[6].row
        assertEquals(listOf("profile-recent:1"), pager.actions()) // no previous page
        assertEquals(5, first.drop(7).size)
        assertTrue("1/3" in pager.text())
        val last = lines(many, page = 2)
        assertEquals(listOf("profile-recent:1"), last[6].row.actions()) // no next page
        assertEquals(2, last.drop(7).size)
        val middle = lines(many, page = 1)
        assertEquals(listOf("profile-recent:0", "profile-recent:2"), middle[6].row.actions())
        assertTrue(lines(sample)[6].row.actions().isEmpty(), "one page needs no pager")
    }

    @Test
    fun `a page past the end shows the last one`() {
        val many = data((1..12).map { row("words 10 english", 50.0 + it, now - it * 1000L) })
        assertEquals(lines(many, page = 2).map { it.row.text() }, lines(many, page = 99).map { it.row.text() })
        assertEquals(lines(many, page = 0).map { it.row.text() }, lines(many, page = -4).map { it.row.text() })
    }

    @Test
    fun `no tests yet`() {
        val l = lines(data(emptyList()))
        assertTrue("0 tests" in l[3].row.text())
        assertTrue(l.any { "no tests yet" in it.row.text() })
        assertTrue(l[4].row.text().count { it == '-' } == 4)
    }

    @Test
    fun `long text never overlaps its neighbours`() {
        val d = data((1..3).map { ResultRow("words 100 english", 1234.56, 1234.56, 100.0, 100.0, 99_999.0, now - it) }, name = "A_very_long_nam")
        for (line in lines(d, own = false) + lines(d, page = 0)) {
            val lefts = line.row.cellLefts()
            val widths = line.row.cellWidths()
            for (i in 1 until lefts.size) assertTrue(lefts[i] >= lefts[i - 1] + widths[i - 1], "${line.row.text()} overlaps at $i")
            assertTrue(lefts.last() + widths.last() <= 150, "${line.row.text()} leaves the screen")
        }
    }

    @Test
    fun `loading and missing profiles`() {
        val loading = ProfileRows.lines(ProfileView.Loading, true, 0, now, { "" }, { "" }, null)
        assertEquals(1, loading.size)
        assertTrue("loading" in loading[0].row.text())
        val missing = ProfileRows.lines(ProfileView.Missing, true, 0, now, { "" }, { "" }, null)
        assertTrue("no such player" in missing[0].row.text())
        assertTrue(missing.any { it.row.actions().contains("lookup") }, "a look up button stays")
    }

    @Test
    fun `the look up button shows what is being typed`() {
        val e = FieldEdit.lookup()
        e.type("Bo")
        val cell = lines(sample, editing = e)[3].row.cells.first { it.action == "lookup" }
        assertTrue(cell.text.startsWith("Bo_"), cell.text)
        assertEquals(Role.MAIN, cell.fill)
        assertFalse(lines(sample)[3].row.cells.first { it.action == "lookup" }.text.contains("_"))
        assertNotNull(lines(sample, editing = e))
    }

    @Test
    fun `the hints explain the rows`() {
        val l = lines(sample)
        assertTrue(l[3].hint!!.contains("tab"), "the look up row mentions completion: ${l[3].hint}")
        assertEquals(null, l[0].hint)
    }

    @Test
    fun `admins carry the server admin prefix in front of the name`() {
        val admin = ProfileBuilder.build(id, "Javaniac", 1621900800000L, emptyList(), face, admin = true)
        val row = lines(admin)[0].row
        assertTrue(row.text().startsWith("[Server Admin]Javaniac") || row.text().startsWith("[Server Admin] Javaniac"), row.text())
        assertFalse(lines(sample)[0].row.text().contains("Server Admin"))
        val long = ProfileBuilder.build(id, "A_very_long_nam", 0L, emptyList(), face, admin = true)
        val cells = lines(long)[0].row
        val lefts = cells.cellLefts()
        val widths = cells.cellWidths()
        for (i in 1 until lefts.size) assertTrue(lefts[i] >= lefts[i - 1] + widths[i - 1], "overlap at $i")
        assertTrue(lefts.last() + widths.last() <= 150)
    }
}
