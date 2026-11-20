package com.javaniac.blockytype.menu

import com.javaniac.blockytype.NetworkField
import com.javaniac.blockytype.Source
import com.javaniac.blockytype.settings.Section
import com.javaniac.blockytype.settings.SettingsCatalog
import com.javaniac.blockytype.ui.Cell
import com.javaniac.blockytype.ui.HudState
import com.javaniac.blockytype.ui.MenuLayout
import com.javaniac.blockytype.ui.MenuRow
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.UiFont
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeHost(override val isAdmin: Boolean = false) : MenuHost {
    val user = mutableMapOf<String, String>()
    val server = mutableMapOf<String, String>()
    val log = mutableListOf<String>()
    var themeChoices = listOf("default", "nord")
    var fields = listOf(
        NetworkField("server-ip", "server ip", "0.0.0.0", Source.DEFAULT, pendingRestart = false),
        NetworkField("server-port", "server port", "25565", Source.FILE, pendingRestart = false),
    )
    override var editing: FieldEdit? = null

    override fun userValue(id: String) = user[id]
    override fun serverValue(id: String) = server[id]
    override fun setUser(id: String, value: String) { user[id] = value; log += "user $id=$value" }
    override fun clearUser(id: String) { user.remove(id); log += "clear $id" }
    override fun setServer(id: String, value: String) { server[id] = value; log += "server $id=$value" }
    override fun resetUserSettings() { user.clear(); log += "reset" }
    override fun restartTest() { log += "restart" }
    override fun close() { log += "close" }
    override fun networkFields() = fields
    override fun beginEdit(key: String) { log += "edit $key"; editing = FieldEdit(key, fields.first { it.key == key }.value) }
    override fun choices(id: String) = when (id) {
        "theme" -> themeChoices
        "font" -> listOf("jetbrains-mono", "fira-code")
        else -> SettingsCatalog.byId(id)!!.options.values()
    }
}

class MenuSessionTest {
    private fun session(host: MenuHost = FakeHost(), page: Page = Page.Settings(Section.BEHAVIOR)) = MenuSession(host, page)

    private fun HudState.Menu.rowAt(slot: Int): MenuRow? = when (slot) {
        MenuLayout.HEADER -> header
        MenuLayout.FOOTER -> footer
        else -> rows.getOrNull(slot - 1)
    }

    private fun HudState.Menu.rowTexts(): List<String> = rows.map { r -> r?.cells?.joinToString(" ") { it.text.trim() } ?: "" }

    private fun MenuSession.focused(): Cell? {
        val v = view()
        val f = v.focus ?: return null
        return v.rowAt(f.slot)?.cells?.get(f.cell)
    }

    /** Navigates with the public input methods only, like a player would. */
    private fun MenuSession.goTo(action: String) {
        repeat(300) { move(-1) }
        for (i in 0..300) {
            repeat(30) { moveCell(-1) }
            for (c in 0..30) {
                if (focused()?.action == action) return
                moveCell(1)
            }
            move(1)
        }
        error("no cell with action $action in ${view().rowTexts()} focus=${view().focus}")
    }

    private fun MenuSession.select(action: String) {
        goTo(action)
        activate()
    }

    // ---- layout

    @Test
    fun `settings page shows a big section title, the section tabs and the first setting with its options`() {
        val v = session().view()
        val title = v.header.cells.first()
        assertEquals("behavior", title.text)
        assertTrue(title.big)
        val texts = v.rowTexts()
        assertTrue("behavior" in texts[0] && "input" in texts[0], texts.toString())
        assertTrue(texts[2].startsWith("difficulty"), texts.toString())
        assertTrue("normal" in texts[2] && "expert" in texts[2] && "master" in texts[2], texts.toString())
    }

    @Test
    fun `the section tabs sit on a strip`() {
        val v = session().view()
        assertEquals(Role.SUB_ALT, v.rows[0]!!.strip)
        assertEquals(Role.SUB_ALT, v.rows[1]!!.strip)
        assertNull(v.rows[2]!!.strip)
    }

    @Test
    fun `the active option is a filled main pill, others sit on the alt background`() {
        val row = session().view().rows[2]!!
        val normal = row.cells.single { it.text == "normal" }
        val expert = row.cells.single { it.text == "expert" }
        assertEquals(Role.MAIN, normal.fill)
        assertEquals(Role.BG, normal.role)
        assertEquals(Role.SUB_ALT, expert.fill)
        assertEquals(Role.TEXT, expert.role)
    }

    @Test
    fun `every row fits the smallest GUI`() {
        for (page in listOf(Page.Settings(Section.BEHAVIOR), Page.Settings(Section.APPEARANCE), Page.Settings(Section.INPUT), Page.Admin)) {
            val s = session(FakeHost(isAdmin = true), page)
            val rows = s.view().let { listOf(it.header) + it.rows.filterNotNull() } + s.contentRows()
            for (row in rows) {
                val lefts = row.cellLefts()
                val widths = row.cellWidths()
                for (i in row.cells.indices) {
                    assertTrue(
                        lefts[i] >= -150 && lefts[i] + widths[i] <= 150,
                        "cell ${row.cells[i].text} leaves the screen: ${lefts[i]}..${lefts[i] + widths[i]} on $page",
                    )
                }
            }
        }
    }

    @Test
    fun `a long title goes on its own row and the options below start in the options column`() {
        val s = session(page = Page.Settings(Section.APPEARANCE))
        val rows = s.contentRows()
        val i = rows.indexOfFirst { it.cells.firstOrNull()?.text?.trim() == "live word burst style" }
        assertTrue(i >= 0)
        assertTrue(rows[i].cells.size == 1, "title alone")
        assertTrue(rows[i + 1].cells.all { it.action != null })
        assertTrue(rows[i + 1].left > -150, "options are indented")
    }

    // ---- navigation

    @Test
    fun `focus starts on the active option of the first setting`() {
        val s = session()
        assertEquals("set:difficulty:normal", s.focused()?.action)
    }

    @Test
    fun `moving down goes to the next setting keeping the column, a and d move between options`() {
        val s = session()
        s.moveCell(1)
        assertEquals("set:difficulty:expert", s.focused()?.action)
        s.move(1)
        assertTrue(s.focused()?.action?.startsWith("set:quick-restart:") == true, s.focused().toString())
        repeat(6) { s.moveCell(1) } // clamped at the last option
        assertEquals("set:quick-restart:enter", s.focused()?.action)
        s.move(-1)
        assertTrue(s.focused()?.action?.startsWith("set:difficulty:") == true)
    }

    @Test
    fun `activating the focused option sets the users value`() {
        val host = FakeHost()
        val s = session(host)
        s.moveCell(1)
        s.activate()
        assertEquals("expert", host.user["difficulty"])
        val row = s.view().rows[2]!!
        assertEquals(Role.MAIN, row.cells.single { it.text == "expert" }.fill)
    }

    @Test
    fun `moving past the top reaches the tabs and the header`() {
        val s = session(FakeHost(isAdmin = false))
        repeat(5) { s.move(-1) }
        assertEquals("page:profile", s.focused()?.action) // the nearest header button, left of close
        s.moveCell(1)
        assertEquals("close", s.focused()?.action)
    }

    @Test
    fun `selecting a tab switches the section`() {
        val s = session()
        s.select("section:sound")
        val texts = s.view().rowTexts()
        assertTrue(texts.any { it.startsWith("sound volume") }, texts.toString())
        assertFalse(texts.any { it.startsWith("difficulty") })
        assertEquals("sound", s.view().header.cells.first().text)
    }

    @Test
    fun `after a tab switch the focus stays on the tabs`() {
        val s = session()
        s.select("section:sound")
        assertEquals("section:sound", s.focused()?.action)
        s.moveCell(1)
        assertEquals("section:caret", s.focused()?.action)
    }

    @Test
    fun `moving down far scrolls the content and keeps the focus visible`() {
        val s = session()
        val before = s.view().rowTexts()
        repeat(12) { s.move(1) }
        val v = s.view()
        assertNotNull(v.focus)
        assertNotNull(v.rowAt(v.focus!!.slot))
        assertTrue(s.scrollOffset > 0)
        assertFalse(v.rowTexts()[2] == before[2], "content moved")
    }

    @Test
    fun `moving back to the top scrolls back to the start`() {
        val s = session()
        repeat(12) { s.move(1) }
        repeat(12) { s.move(-1) }
        assertEquals(0, s.scrollOffset)
    }

    @Test
    fun `focus never rests on a row without options`() {
        val s = session()
        repeat(40) {
            s.move(1)
            assertNotNull(s.focused()?.action, "step $it")
        }
    }

    // ---- footer

    @Test
    fun `the footer shows the description of the focused setting, cut to one line`() {
        val s = session()
        val text = s.view().footer!!.cells.joinToString("") { it.text }
        assertTrue(text.startsWith("Normal is the classic"), text)
        assertTrue(text.length <= UiFont.MAX_LINE_CHARS, "footer ${text.length} chars")
    }

    private fun MenuSession.footerText() = view().footer!!.cells.joinToString("") { it.text }

    private val sec = 1_000_000_000L

    @Test
    fun `a long description scrolls through the footer until its end is shown`() {
        val s = session()
        val full = SettingsCatalog.byId("difficulty")!!.description
        s.tick(0)
        assertTrue(full.startsWith(s.footerText()))
        assertTrue(s.tick((Marquee.HOLD_STEPS + full.length - UiFont.MAX_LINE_CHARS) * Marquee.STEP_NANOS), "the window moved")
        val shown = s.footerText()
        assertEquals(UiFont.MAX_LINE_CHARS, shown.length)
        assertTrue(full.endsWith(shown), shown)
    }

    @Test
    fun `the footer scroll stays put while nothing changes and starts over after a focus change`() {
        val s = session()
        s.tick(0)
        assertFalse(s.tick(100_000_000L), "still in the initial pause")
        s.tick(30 * sec)
        s.move(1)
        s.tick(31 * sec)
        val text = s.footerText()
        assertTrue(SettingsCatalog.byId("quick-restart")!!.description.startsWith(text), text)
    }

    @Test
    fun `a short text never scrolls`() {
        val s = session(FakeHost(isAdmin = true), Page.Admin)
        s.tick(0)
        val before = s.footerText()
        assertFalse(s.tick(50 * sec))
        assertEquals(before, s.footerText())
        assertTrue(before.length < UiFont.MAX_LINE_CHARS)
    }

    @Test
    fun `without a focused setting the footer shows the key hints`() {
        val s = session()
        repeat(5) { s.move(-1) } // up to the header
        val text = s.view().footer!!.cells.joinToString("") { it.text }
        assertTrue("space" in text && text.length <= UiFont.MAX_LINE_CHARS, text)
    }

    // ---- server defaults

    @Test
    fun `server default settings offer a default option that clears the override`() {
        val host = FakeHost()
        val s = session(host, Page.Settings(Section.APPEARANCE))
        val row = s.contentRows().first { r -> r.cells.any { it.action == "set:animation:off" } }
        assertTrue(row.cells.any { it.action == "clear:animation" })
        assertEquals(Role.MAIN, row.cells.single { it.action == "clear:animation" }.fill) // no override yet
        s.select("set:animation:off")
        s.select("clear:animation")
        assertEquals(listOf("user animation=off", "clear animation"), host.log)
    }

    @Test
    fun `close cell in the header closes the menu`() {
        val host = FakeHost()
        session(host).select("close")
        assertEquals(listOf("close"), host.log)
    }

    // ---- admin

    @Test
    fun `admin tab only for admins`() {
        assertFalse(session(FakeHost(isAdmin = false)).view().rowTexts().any { "admin" in it })
        assertTrue(session(FakeHost(isAdmin = true)).view().rowTexts().any { "admin" in it })
    }

    @Test
    fun `crafted admin actions from non admins change nothing`() {
        val host = FakeHost(isAdmin = false)
        val s = session(host)
        s.perform("sd:animation:off")
        s.perform("section:admin")
        s.perform("page:admin")
        s.perform("edit:server-ip")
        assertEquals(emptyList(), host.log)
        assertTrue(s.view().rowTexts()[0].contains("behavior"), "stayed on the settings page")
    }

    @Test
    fun `admin sets server defaults`() {
        val host = FakeHost(isAdmin = true)
        val s = session(host)
        s.perform("section:admin")
        s.select("sd:animation:off")
        assertEquals("off", host.server["animation"])
    }

    @Test
    fun `the admin page lists the network settings, editable ones with an edit action`() {
        val s = session(FakeHost(isAdmin = true), Page.Admin)
        val rows = s.contentRows()
        val ip = rows.first { r -> r.cells.any { it.text.trim() == "server ip" } }
        assertTrue(ip.cells.any { it.action == "edit:server-ip" && it.text == "0.0.0.0" })
        val port = rows.first { r -> r.cells.any { it.text.trim() == "server port" } }
        assertTrue(port.cells.any { it.action == "edit:server-port" })
    }

    @Test
    fun `selecting an editable network value starts an edit`() {
        val host = FakeHost(isAdmin = true)
        val s = session(host, Page.Admin)
        s.select("edit:server-ip")
        assertEquals(listOf("edit server-ip"), host.log)
    }

    @Test
    fun `crafted edit actions for unknown keys do nothing`() {
        val host = FakeHost(isAdmin = true)
        val s = session(host, Page.Admin)
        s.perform("edit:font")
        s.perform("edit:../../x")
        s.perform("edit")
        assertEquals(emptyList(), host.log)
    }

    @Test
    fun `while editing the value shows the draft and the footer explains the keys`() {
        val host = FakeHost(isAdmin = true)
        val s = session(host, Page.Admin)
        s.select("edit:server-ip")
        host.editing!!.type("10.0.0.5")
        val v = s.view()
        assertTrue(s.contentRows().any { r -> r.cells.any { it.text.startsWith("10.0.0.5") } })
        val footer = v.footer!!.cells.joinToString("") { it.text }
        assertTrue("enter" in footer.lowercase() && footer.length <= UiFont.MAX_LINE_CHARS, footer)
    }

    @Test
    fun `an invalid edit shows its error in the footer`() {
        val host = FakeHost(isAdmin = true)
        val s = session(host, Page.Admin)
        s.select("edit:server-ip")
        host.editing!!.commit("a b")
        val footer = s.view().footer!!.cells.joinToString("") { it.text }
        assertTrue("invalid" in footer, footer)
    }

    // ---- validation

    @Test
    fun `invalid values are rejected`() {
        val host = FakeHost()
        val s = session(host)
        s.perform("set:difficulty:nonsense")
        s.perform("set:nope:x")
        s.perform("set:difficulty")
        s.perform("garbage")
        assertEquals(emptyList(), host.log)
    }

    @Test
    fun `reset settings in the danger zone clears every override`() {
        val host = FakeHost()
        host.user["theme"] = "nord"
        val s = session(host, Page.Settings(Section.DANGER_ZONE))
        s.select("reset-all")
        assertEquals(emptyMap(), host.user)
    }

    // ---- language picker

    private fun MenuSession.languageRow(): List<String> =
        contentRows().first { it.cells.firstOrNull()?.text?.trim() == "language" }.cells.map { it.text.trim() }

    @Test
    fun `the language setting shows the current language and a browse button, not every language`() {
        val row = session().languageRow()
        assertTrue("english" in row && "browse" in row, "$row")
        assertFalse("german" in row)
    }

    @Test
    fun `browse opens the language pager with english first`() {
        val s = session()
        s.select("page:languages")
        val v = s.view()
        assertEquals("language", v.header.cells.first().text)
        assertEquals("english", v.rows.first()!!.cells.first().text.trim())
        val pager = v.rows.filterNotNull().last().cells.map { it.text.trim() }
        assertTrue(pager.any { it.startsWith("page 1/") }, "$pager")
    }

    @Test
    fun `the pager moves through all languages alphabetically after english`() {
        val s = session()
        s.select("page:languages")
        val seen = mutableListOf<String>()
        var page = 0
        while (true) {
            seen += s.view().rows.filterNotNull().dropLast(1).flatMap { r -> r.cells.map { it.action } }.filterNotNull()
            if (s.view().rows.filterNotNull().last().cells.none { it.action == "langpage:${page + 1}" }) break
            s.select("langpage:${page + 1}")
            page++
        }
        val names = seen.map { it.removePrefix("set:language:") }
        assertEquals(com.javaniac.blockytype.words.LanguageRegistry.names(), names)
        assertTrue(page >= 1)
    }

    @Test
    fun `back from the pager returns to the settings`() {
        val s = session()
        s.select("page:languages")
        s.select("back")
        assertEquals("behavior", s.view().header.cells.first().text)
    }

    @Test
    fun `selecting a language sets it`() {
        val host = FakeHost()
        val s = session(host)
        s.select("page:languages")
        for (p in 1..com.javaniac.blockytype.words.LanguageRegistry.names().indexOf("german") / MenuSession.LANGUAGE_PAGE_SIZE) {
            s.select("langpage:$p")
        }
        s.select("set:language:german")
        assertEquals("german", host.user["language"])
    }

    @Test
    fun `unknown and unlisted languages are rejected`() {
        val host = FakeHost()
        val s = session(host)
        s.perform("set:language:klingon")
        s.perform("set:language:code_python")
        assertNull(host.user["language"])
        assertTrue(host.log.isEmpty())
    }

    @Test
    fun `languages that must be downloaded are marked with a star`() {
        val s = session()
        s.select("page:languages")
        val texts = s.view().rows.filterNotNull().flatMap { r -> r.cells.map { it.text.trim() } }
        assertTrue("english 10k *" in texts, "$texts")
        assertTrue("english 1k" in texts, "$texts")
    }
}
