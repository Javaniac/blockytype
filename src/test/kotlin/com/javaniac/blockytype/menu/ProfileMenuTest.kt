package com.javaniac.blockytype.menu

import com.javaniac.blockytype.profile.ProfileBuilder
import com.javaniac.blockytype.profile.ResultRow
import com.javaniac.blockytype.settings.Section
import com.javaniac.blockytype.ui.Cell
import com.javaniac.blockytype.ui.MenuLayout
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class ProfileHost(override val selfId: UUID) : MenuHost by FakeHost() {
    val views = mutableMapOf<UUID, ProfileView>()
    val log = mutableListOf<String>()
    override var editing: FieldEdit? = null
    override fun profileOf(uuid: UUID): ProfileView = views[uuid] ?: ProfileView.Missing.also { log += "missing $uuid" }
    override fun beginLookup() { log += "lookup" }
    override fun close() { log += "close" }
}

class ProfileMenuTest {
    private val me = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val other = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun data(id: UUID, name: String, tests: Int) = ProfileBuilder.build(
        id, name, 1621900800000L,
        (1..tests).map { ResultRow("words 10 english", 60.0 + it, 70.0, 97.0, 80.0, 10.0, it * 1000L) }, IntArray(64),
    )

    private fun host(): ProfileHost = ProfileHost(me).also {
        it.views[me] = ProfileView.Ready(data(me, "Alice", 12))
        it.views[other] = ProfileView.Ready(data(other, "Bob", 2))
    }

    private fun MenuSession.focusedAction(): String? = view().let { v ->
        val f = v.focus ?: return null
        val row = when (f.slot) { MenuLayout.HEADER -> v.header; else -> v.rows[f.slot - 1] }
        row?.cells?.getOrNull(f.cell)?.action
    }

    private fun MenuSession.headerActions(): List<String> = view().header.cells.mapNotNull(Cell::action)

    @Test
    fun `the settings header has a profile button that opens the own profile`() {
        val h = host()
        val s = MenuSession(h, Page.Settings(Section.BEHAVIOR))
        assertTrue("page:profile" in s.headerActions())
        s.perform("page:profile")
        assertEquals("Alice", s.view().header.cells.first().text)
        assertTrue("back" in s.headerActions(), "back leads to the settings")
        assertTrue("page:profile" !in s.headerActions(), "no profile button on the profile page")
        s.perform("back")
        assertTrue(s.view().header.cells.first().text == "behavior")
    }

    @Test
    fun `a profile that arrives later gets the focus on its look up button`() {
        val h = ProfileHost(me)
        h.views[me] = ProfileView.Loading
        val s = MenuSession(h, Page.Profile(me))
        assertTrue(s.focusedAction() != "lookup", "nothing to select while loading")
        h.views[me] = ProfileView.Ready(data(me, "Alice", 3))
        assertEquals("lookup", s.focusedAction())
        // the focus is only taken once: a later move stays where the player put it
        s.moveCell(1)
        h.views[me] = ProfileView.Ready(data(me, "Alice", 4))
        assertEquals("lookup", s.focusedAction().let { it }) // single button row: still the same cell
    }

    @Test
    fun `the age column does not touch the consistency`() {
        val s = MenuSession(host(), Page.Profile(me))
        val row = s.view().rows.last()!!
        val lefts = row.cellLefts()
        val widths = row.cellWidths()
        val cons = row.cells.indexOfFirst { it.text.endsWith("cons") }
        assertTrue(lefts[cons + 1] >= lefts[cons] + widths[cons] + 6, "one char between consistency and age")
    }

    @Test
    fun `the profile page starts on its look up button`() {
        val s = MenuSession(host(), Page.Profile(me))
        assertEquals("lookup", s.focusedAction())
    }

    @Test
    fun `look up starts typing a name`() {
        val h = host()
        val s = MenuSession(h, Page.Profile(me))
        s.activate()
        assertEquals(listOf("lookup"), h.log)
    }

    @Test
    fun `look up does nothing outside a profile page`() {
        val h = host()
        MenuSession(h, Page.Settings(Section.BEHAVIOR)).perform("lookup")
        MenuSession(h, Page.Admin).perform("lookup")
        assertTrue("lookup" !in h.log)
    }

    @Test
    fun `showing a profile opens it, from another profile it replaces that page`() {
        val h = host()
        val s = MenuSession(h, Page.Settings(Section.BEHAVIOR))
        s.showProfile(other)
        assertEquals("Bob", s.view().header.cells.first().text)
        s.showProfile(me)
        assertEquals("Alice", s.view().header.cells.first().text)
        s.perform("back") // one back leaves the profiles
        assertEquals("behavior", s.view().header.cells.first().text)
    }

    @Test
    fun `the my profile button goes back to the own profile`() {
        val s = MenuSession(host(), Page.Profile(other))
        s.perform("profile:self")
        assertEquals("Alice", s.view().header.cells.first().text)
    }

    @Test
    fun `recent test pages`() {
        val s = MenuSession(host(), Page.Profile(me))
        fun firstRecent() = s.view().rows.last()!!.cells.first().text
        assertTrue(s.view().rows.any { r -> r?.cells?.any { it.action == "profile-recent:1" } == true })
        s.perform("profile-recent:1")
        val second = s.view().rows.filterNotNull().map { r -> r.cells.joinToString("") { it.text } }
        assertTrue(second.any { "2/3" in it }, second.toString())
        s.perform("profile-recent:2")
        assertTrue(s.view().rows.filterNotNull().any { r -> r.cells.joinToString("") { it.text }.contains("3/3") })
        assertNotNull(firstRecent())
    }

    @Test
    fun `bad recent pages and other bad actions do nothing`() {
        val s = MenuSession(host(), Page.Profile(me))
        val before = s.view().rows.map { r -> r?.cells?.joinToString("") { it.text } }
        for (a in listOf("profile-recent:9", "profile-recent:-1", "profile-recent:x", "profile-recent", "profile:other", "profile:", "page:nope")) s.perform(a)
        assertEquals(before, s.view().rows.map { r -> r?.cells?.joinToString("") { it.text } })
        val plain = MenuSession(host(), Page.Settings(Section.BEHAVIOR))
        plain.perform("profile-recent:1")
        assertEquals("behavior", plain.view().header.cells.first().text)
    }

    @Test
    fun `a missing profile still has a look up button`() {
        val h = ProfileHost(me)
        val s = MenuSession(h, Page.Profile(UUID.randomUUID()))
        assertEquals("lookup", s.focusedAction())
        assertEquals("profile", s.view().header.cells.first().text)
    }

    @Test
    fun `the footer guides the lookup`() {
        val h = host()
        val s = MenuSession(h, Page.Profile(me))
        h.editing = FieldEdit.lookup()
        s.tick(0)
        assertTrue(s.view().footer!!.cells.first().text.startsWith("right click: type a name"), s.view().footer!!.cells.first().text)
        h.editing!!.fail("never joined")
        s.tick(1)
        assertTrue(s.view().footer!!.cells.first().text.startsWith("never joined"), s.view().footer!!.cells.first().text)
    }

    @Test
    fun `the page fits the menu`() {
        val s = MenuSession(host(), Page.Profile(me))
        assertTrue(s.view().rows.size <= MenuLayout.BODY_ROWS)
    }
}
