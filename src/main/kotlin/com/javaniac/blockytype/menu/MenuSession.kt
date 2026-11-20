package com.javaniac.blockytype.menu

import com.javaniac.blockytype.profile.ProfileData
import com.javaniac.blockytype.settings.Section
import com.javaniac.blockytype.settings.Setting
import com.javaniac.blockytype.settings.SettingsCatalog
import com.javaniac.blockytype.ui.Cell
import com.javaniac.blockytype.ui.Focus
import com.javaniac.blockytype.ui.HudState
import com.javaniac.blockytype.ui.MenuLayout
import com.javaniac.blockytype.ui.MenuRow
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.UiFont
import com.javaniac.blockytype.words.LanguageRegistry
import java.util.UUID

/**
 * One player's open menu: the page stack, the focused cell and the scroll position. Pure logic: turns the
 * state into a [HudState.Menu] and actions (strings from the cells' `action`) into [MenuHost] calls.
 * Navigation is keyboard like: [move] (W, S, wheel), [moveCell] (A, D) and [activate] (space, left click).
 * Actions come from the cells of the current view, but a client can always craft packets, so [perform]
 * validates everything again.
 */
class MenuSession(private val host: MenuHost, start: Page) {
    private val stack = ArrayDeque<Page>()
    private var page: Page = start

    var scrollOffset = 0
        private set

    /** Index into [Layout.lines]: 0 is the header, then the tab rows, then the content. */
    private var focusRow = 0
    private var focusCell = 0

    /** A row and what the footer says while it is focused. */
    private class Line(val row: MenuRow, val settingId: String? = null, val hint: String? = null) {
        val actionable: Boolean get() = row.cells.any { it.action != null }
    }

    private class Layout(val header: Line, val tabs: List<Line>, val content: List<Line>) {
        val lines: List<Line> = listOf(header) + tabs + content
        val contentStart: Int get() = 1 + tabs.size
        val slots: Int get() = MenuLayout.BODY_ROWS - tabs.size
    }

    /** The page had nothing to select when it opened (a profile still loading): the focus is taken once it has. */
    private var needsFocus = false

    init {
        focusFirstContent()
    }

    private fun refocus() {
        if (!needsFocus) return
        val l = layout()
        if ((l.contentStart until l.lines.size).any { l.lines[it].actionable }) focusFirstContent()
    }

    // ---- input

    /** Focus moves to the previous ([dy] < 0) or next row that has options, keeping its column. */
    fun move(dy: Int) {
        if (dy == 0) return
        refocus()
        val l = layout()
        focusRow = focusRow.coerceIn(l.lines.indices)
        val step = if (dy < 0) -1 else 1
        var i = focusRow + step
        while (i in l.lines.indices && !l.lines[i].actionable) i += step
        if (i !in l.lines.indices) return
        val from = l.lines[focusRow.coerceIn(l.lines.indices)].row
        val centerX = from.cellLefts().getOrNull(focusCell)?.let { it + from.cellWidths()[focusCell] / 2 } ?: 0
        focusRow = i
        focusCell = nearestCell(l.lines[i].row, centerX)
        ensureVisible(l)
    }

    /** Focus moves to the previous ([dx] < 0) or next option of the row. */
    fun moveCell(dx: Int) {
        if (dx == 0) return
        refocus()
        val l = layout()
        val row = l.lines.getOrNull(focusRow)?.row ?: return
        val step = if (dx < 0) -1 else 1
        var i = focusCell + step
        while (i in row.cells.indices && row.cells[i].action == null) i += step
        if (i in row.cells.indices) focusCell = i
    }

    /** Runs the action of the focused cell. */
    fun activate() {
        refocus()
        val l = layout()
        val action = l.lines.getOrNull(focusRow)?.row?.cells?.getOrNull(focusCell)?.action ?: return
        perform(action)
    }

    private fun nearestCell(row: MenuRow, centerX: Int): Int {
        val lefts = row.cellLefts()
        val widths = row.cellWidths()
        return row.cells.indices.filter { row.cells[it].action != null }
            .minByOrNull { kotlin.math.abs(lefts[it] + widths[it] / 2 - centerX) } ?: 0
    }

    private fun focusFirstContent() {
        val l = layout()
        scrollOffset = 0
        val i = (l.contentStart until l.lines.size).firstOrNull { l.lines[it].actionable }
            ?: l.lines.indices.firstOrNull { l.lines[it].actionable } ?: 0
        focusRow = i
        focusCell = preferredCell(l.lines[i].row)
        needsFocus = (l.contentStart until l.lines.size).none { l.lines[it].actionable }
        ensureVisible(l)
    }

    /** The active option of a row, else its first option. */
    private fun preferredCell(row: MenuRow): Int =
        row.cells.indexOfFirst { it.action != null && it.fill == Role.MAIN }.takeIf { it >= 0 }
            ?: row.cells.indexOfFirst { it.action != null }.coerceAtLeast(0)

    private fun maxScroll(l: Layout) = (l.content.size - l.slots).coerceAtLeast(0)

    /** Scrolls so the focused content row (and the title row above it) is shown. */
    private fun ensureVisible(l: Layout) {
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(l))
        if (focusRow < l.contentStart) return
        val j = focusRow - l.contentStart
        val firstActionable = l.content.indexOfFirst { it.actionable }
        if (j == firstActionable) {
            scrollOffset = 0
            return
        }
        val above = l.content.getOrNull(j - 1)
        val top = if (above != null && !above.actionable && above.settingId != null && above.settingId == l.content[j].settingId) j - 1 else j
        if (top < scrollOffset) scrollOffset = top
        if (j >= scrollOffset + l.slots) scrollOffset = j - l.slots + 1
        scrollOffset = scrollOffset.coerceIn(0, maxScroll(l))
    }

    // ---- actions

    private fun navigate(to: Page, push: Boolean, keepFocus: Boolean = false) {
        if (push) stack.addLast(page) else if (!keepFocus) stack.clear()
        page = to
        if (keepFocus) {
            scrollOffset = 0
            val l = layout()
            // The new page can have fewer rows or cells than the old one: keep the focus on something that exists.
            focusRow = focusRow.coerceIn(l.lines.indices)
            focusCell = focusCell.coerceIn(0, (l.lines[focusRow].row.cells.size - 1).coerceAtLeast(0))
            ensureVisible(l)
        } else {
            focusFirstContent()
        }
    }

    /** Runs an action. Unknown, malformed, unauthorised or invalid actions do nothing. */
    fun perform(action: String) {
        val parts = action.split(":", limit = 3)
        when (parts[0]) {
            "close" -> host.close()
            "restart" -> { host.restartTest(); host.close() }
            "back" -> stack.removeLastOrNull()?.let { page = it; focusFirstContent() }
            "page" -> when (parts.getOrNull(1)) {
                "settings" -> navigate(Page.Settings(Section.BEHAVIOR), push = true)
                "admin" -> if (host.isAdmin) navigate(Page.Admin, push = true)
                "profile" -> showProfile(host.selfId)
                "languages" -> navigate(Page.Languages(languagePageOf(current(SettingsCatalog.byId("language")!!))), push = true)
            }
            "langpage" -> {
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (page is Page.Languages && index in 0 until languagePages()) {
                    navigate(Page.Languages(index), push = false, keepFocus = true)
                }
            }
            "section" -> {
                val name = parts.getOrNull(1) ?: return
                if (name == "admin") {
                    if (host.isAdmin) navigate(Page.Admin, push = false, keepFocus = true)
                } else {
                    Section.entries.firstOrNull { it.label == name }
                        ?.let { navigate(Page.Settings(it), push = false, keepFocus = true) }
                }
            }
            "set" -> {
                val (id, value) = idValue(parts) ?: return
                host.setUser(id, value)
            }
            "clear" -> parts.getOrNull(1)?.takeIf { SettingsCatalog.byId(it)?.serverDefault == true }?.let { host.clearUser(it) }
            "sd" -> {
                if (!host.isAdmin) return
                val (id, value) = idValue(parts) ?: return
                if (SettingsCatalog.byId(id)?.serverDefault != true) return
                host.setServer(id, value)
            }
            "edit" -> {
                val key = parts.getOrNull(1) ?: return
                if (!host.isAdmin) return
                if (host.networkFields().none { it.key == key }) return
                host.beginEdit(key)
            }
            "reset-all" -> host.resetUserSettings()
            "lookup" -> if (page is Page.Profile) host.beginLookup()
            "profile" -> if (parts.getOrNull(1) == "self") showProfile(host.selfId)
            "profile-recent" -> {
                val p = page as? Page.Profile ?: return
                val index = parts.getOrNull(1)?.toIntOrNull() ?: return
                val pages = (profileData(p.uuid)?.recent?.size ?: 0).let { (it + ProfileRows.RECENT_PER_PAGE - 1) / ProfileRows.RECENT_PER_PAGE }
                if (index in 0 until pages) navigate(Page.Profile(p.uuid, index), push = false, keepFocus = true)
            }
        }
    }

    /** Opens the profile of [uuid]; from another profile it replaces that page, so back still leads out of the profiles. */
    fun showProfile(uuid: UUID) {
        if (page is Page.Profile) navigate(Page.Profile(uuid), push = false, keepFocus = true) else navigate(Page.Profile(uuid), push = true)
    }

    private fun profileData(uuid: UUID): ProfileData? = (host.profileOf(uuid) as? ProfileView.Ready)?.data

    /** `set:<id>:<value>` parts to (id, value) if the setting exists and the value is one of its choices. */
    private fun idValue(parts: List<String>): Pair<String, String>? {
        if (parts.size < 3) return null
        val id = parts[1]
        val value = parts[2]
        if (SettingsCatalog.byId(id) == null || value !in host.choices(id)) return null
        return id to value
    }

    // ---- view

    fun view(): HudState.Menu {
        refocus()
        val l = layout()
        ensureVisible(l)
        focusRow = focusRow.coerceIn(l.lines.indices)
        val shown = l.content.drop(scrollOffset).take(l.slots)
        val rows = l.tabs.map { it.row } + shown.map { it.row }
        val focusLine = l.lines[focusRow]
        val slot = when {
            focusRow == 0 -> MenuLayout.HEADER
            focusRow < l.contentStart -> focusRow
            else -> {
                val j = focusRow - l.contentStart - scrollOffset
                if (j in 0 until l.slots) l.tabs.size + 1 + j else -1
            }
        }
        val focus = if (slot >= 0 && focusLine.row.cells.getOrNull(focusCell)?.action != null) Focus(slot, focusCell) else null
        return HudState.Menu(l.header.row, rows, footer(focusLine), focus)
    }

    /** The scrollable rows of the current page (without the tabs), for tests. */
    fun contentRows(): List<MenuRow> = layout().content.map { it.row }

    private fun footerFull(focused: Line): String =
        host.editing?.let { e ->
            when {
                e.error != null -> "${e.error}  right click to try again"
                e.isLookup -> "right click: type a name  tab: complete  enter: open  q: cancel"
                else -> "right click: type  enter: save  q: cancel"
            }
        } ?: focused.settingId?.let { SettingsCatalog.byId(it)?.description } ?: focused.hint ?: KEY_HINTS

    /** What the footer showed last, and when it appeared: a long text scrolls from that moment. */
    private var footerShown = ""
    private var footerSince = 0L
    private var footerOffset = 0

    /**
     * Advances the footer scroll to [now] (nanoseconds). Returns true when the shown window moved, so the caller
     * redraws. A new footer text starts again from its beginning.
     */
    fun tick(now: Long): Boolean {
        val l = layout()
        val text = footerFull(l.lines[focusRow.coerceIn(l.lines.indices)])
        if (text != footerShown) {
            footerShown = text
            footerSince = now
            val moved = footerOffset != 0
            footerOffset = 0
            return moved
        }
        val offset = Marquee.offset(text.length, UiFont.MAX_LINE_CHARS, now - footerSince)
        if (offset == footerOffset) return false
        footerOffset = offset
        return true
    }

    private fun footer(focused: Line): MenuRow {
        val text = footerFull(focused)
        // A text that [tick] has not seen yet (the focus just moved) shows from its start.
        val offset = if (text == footerShown) footerOffset else 0
        val shown = text.drop(offset).take(UiFont.MAX_LINE_CHARS)
        return MenuRow(listOf(Cell(shown, Role.SUB)), left = -150)
    }

    // ---- pages

    private fun layout(): Layout {
        val content = contentList()
        return Layout(Line(headerRow()), tabLines(), content)
    }

    private fun pageTitle(): String = when (val p = page) {
        is Page.Settings -> p.section.label
        Page.Admin -> "admin"
        is Page.Languages -> "language"
        is Page.Profile -> (profileData(p.uuid)?.name ?: "profile")
    }

    private fun headerRow(): MenuRow {
        val title = pageTitle()
        val big = title.length * UiFont.MONO_BIG_ADV <= BIG_TITLE_MAX
        val cells = mutableListOf(Cell(title, Role.SUB, big = big))
        val close = Cell("close", Role.TEXT, "close", fill = Role.SUB_ALT, pad = 1, at = 0)
        var x = 150 - close.width
        cells += close.copy(at = x)
        if (page is Page.Settings || page is Page.Admin) {
            val profile = Cell("profile", Role.TEXT, "page:profile", fill = Role.SUB_ALT, pad = 1)
            x -= TAB_GAP + profile.width
            cells += profile.copy(at = x)
        }
        if (stack.isNotEmpty()) {
            val back = Cell("back", Role.TEXT, "back", fill = Role.SUB_ALT, pad = 1)
            x -= TAB_GAP + back.width
            cells += back.copy(at = x)
        }
        // left to right, so A and D move the focus in the order the cells are drawn
        return MenuRow(cells.sortedBy { it.at ?: Int.MIN_VALUE }, left = -150)
    }

    private fun hasTabs() = page is Page.Settings || page is Page.Admin

    private fun tabLines(): List<Line> {
        if (!hasTabs()) return emptyList()
        fun tab(label: String, action: String, active: Boolean) =
            Cell(label, if (active) Role.MAIN else Role.SUB, action, pad = 1)
        val current = (page as? Page.Settings)?.section
        val sections = Section.entries.map { tab(it.label, "section:${it.label}", it == current) }
        val first = sections.take(5)
        val rest = sections.drop(5).toMutableList()
        if (host.isAdmin) rest += tab("admin", "section:admin", page is Page.Admin)
        val width = first.sumOf { it.width }
        val gap = ((MenuRow.STRIP_WIDTH - width) / (first.size - 1)).coerceAtLeast(TAB_GAP)
        return listOf(
            Line(MenuRow(first, -150, gap, strip = Role.SUB_ALT)),
            Line(MenuRow(rest, -150, gap, strip = Role.SUB_ALT)),
        )
    }

    private fun contentList(): List<Line> = when (val p = page) {
        is Page.Settings -> SettingsCatalog.all.filter { it.section == p.section }.flatMap { settingRows(it) }
        Page.Admin -> adminRows()
        is Page.Languages -> languageRows(p.index)
        is Page.Profile -> profileRows(p)
    }

    private fun profileRows(p: Page.Profile): List<Line> = ProfileRows.lines(
        host.profileOf(p.uuid), own = p.uuid == host.selfId, recentPage = p.recent, now = host.now(),
        speed = host::speed, percent = host::percent, editing = host.editing,
    ).map { Line(it.row, hint = it.hint) }

    private fun current(setting: Setting): String =
        host.userValue(setting.id) ?: host.serverValue(setting.id) ?: setting.default

    /** An option button: the active one is a filled main pill, the others sit on the alt background. */
    private fun pill(text: String, active: Boolean, action: String?) = Cell(
        text.take(MAX_PILL_CHARS),
        if (active) Role.BG else Role.TEXT,
        action,
        fill = if (active) Role.MAIN else Role.SUB_ALT,
        pad = 1,
    )

    /** Packs [cells] into rows of at most [available] units. */
    private fun wrap(cells: List<Cell>, available: Int): List<List<Cell>> {
        val rows = mutableListOf<MutableList<Cell>>()
        var width = 0
        for (cell in cells) {
            val w = cell.width
            val needed = if (rows.isEmpty() || rows.last().isEmpty()) w else width + OPTION_GAP + w
            if (rows.isEmpty() || needed > available) {
                rows += mutableListOf(cell)
                width = w
            } else {
                rows.last() += cell
                width = needed
            }
        }
        return rows
    }

    /**
     * Monkeytype's two columns: the title on the left, the option pills on the right. A title that does not fit
     * the left column gets its own row. Long option lists wrap into more rows of the right column.
     */
    private fun layoutSetting(settingId: String, title: String, pills: List<Cell>): List<Line> {
        val rows = wrap(pills, 150 - OPTIONS_LEFT).ifEmpty { listOf(emptyList()) }
        val lines = mutableListOf<Line>()
        if (title.length <= TITLE_COLS) {
            val titleCell = Cell(title.padEnd(TITLE_COLS + 1), Role.SUB)
            lines += Line(MenuRow(listOf(titleCell) + rows[0], -150, OPTION_GAP), settingId)
            rows.drop(1).forEach { lines += Line(MenuRow(it, OPTIONS_LEFT, OPTION_GAP), settingId) }
        } else {
            lines += Line(MenuRow(listOf(Cell(title, Role.SUB)), -150, OPTION_GAP), settingId)
            rows.forEach { lines += Line(MenuRow(it, OPTIONS_LEFT, OPTION_GAP), settingId) }
        }
        return lines
    }

    private fun settingRows(setting: Setting): List<Line> {
        if (setting.id == "reset-settings") {
            val reset = Cell("reset settings", Role.BG, "reset-all", fill = Role.ERROR, pad = 1)
            return layoutSetting(setting.id, setting.title, listOf(reset))
        }
        val cur = current(setting)
        if (setting.options.paged) {
            val shown = LanguageRegistry.entry(cur)?.display ?: cur
            val pager = listOf(pill(shown, true, null), pill("browse", false, "page:languages"))
            return layoutSetting(setting.id, setting.title, pager)
        }
        val pills = host.choices(setting.id).map { v -> pill(v, v == cur, "set:${setting.id}:$v") }.toMutableList()
        if (setting.serverDefault) {
            pills += pill("default", host.userValue(setting.id) == null, "clear:${setting.id}")
        }
        return layoutSetting(setting.id, setting.title, pills)
    }

    private fun languagePages() = (LanguageRegistry.entries().size + LANGUAGE_PAGE_SIZE - 1) / LANGUAGE_PAGE_SIZE

    private fun languagePageOf(name: String): Int =
        (LanguageRegistry.names().indexOf(name).coerceAtLeast(0) / LANGUAGE_PAGE_SIZE).coerceIn(0, languagePages() - 1)

    /** A page of language pills (the current one filled, lists that still need a download marked `*`) and the pager. */
    private fun languageRows(index: Int): List<Line> {
        val cur = current(SettingsCatalog.byId("language")!!)
        val pages = languagePages()
        val at = index.coerceIn(0, pages - 1)
        val pills = LanguageRegistry.entries().drop(at * LANGUAGE_PAGE_SIZE).take(LANGUAGE_PAGE_SIZE).map { e ->
            val star = if (LanguageRegistry.isAvailable(e.name)) "" else " *"
            pill(e.display + star, e.name == cur, "set:language:${e.name}")
        }
        val hint = "* downloads the word list when selected"
        val lines = wrap(pills, 300).map { Line(MenuRow(it, -150, OPTION_GAP), hint = hint) }.toMutableList()
        val prev = Cell("prev", Role.TEXT, if (at > 0) "langpage:${at - 1}" else null, fill = Role.SUB_ALT, pad = 1)
        val label = Cell("page ${at + 1}/$pages", Role.SUB)
        val next = Cell("next", Role.TEXT, if (at < pages - 1) "langpage:${at + 1}" else null, fill = Role.SUB_ALT, pad = 1)
        lines += Line(MenuRow(listOf(prev, label, next), -150, OPTION_GAP), hint = hint)
        return lines
    }

    private fun adminRows(): List<Line> {
        val lines = mutableListOf<Line>()
        lines += Line(MenuRow(listOf(Cell("network (applies after a restart)", Role.SUB)), -150))
        for (f in host.networkFields()) {
            val edit = host.editing?.takeIf { it.key == f.key }
            val action = if (host.isAdmin) "edit:${f.key}" else null
            val shown = if (edit != null) edit.draft + "_" else f.value
            val value = if (shown.length > NET_VALUE_CHARS) shown.take(NET_VALUE_CHARS - 2) + ".." else shown
            val pill = if (edit != null) {
                Cell(value, Role.BG, action, fill = Role.MAIN, pad = 1)
            } else {
                Cell(value, Role.TEXT, action, fill = Role.SUB_ALT, pad = 1)
            }
            val note = when {
                edit?.error != null -> "invalid"
                f.pendingRestart -> "restart"
                else -> ""
            }
            val hint = "select to type a new value (needs a restart)"
            lines += Line(
                MenuRow(
                    listOf(Cell(f.label.padEnd(NET_LABEL_COLS), Role.SUB), pill, Cell(note, if (edit?.error != null) Role.ERROR else Role.SUB)),
                    -150, OPTION_GAP,
                ),
                hint = hint,
            )
        }
        lines += Line(MenuRow(listOf(Cell("server defaults", Role.SUB)), -150))
        for (setting in SettingsCatalog.all.filter { it.serverDefault }) {
            val cur = host.serverValue(setting.id) ?: setting.default
            val pills = host.choices(setting.id).map { v -> pill(v, v == cur, "sd:${setting.id}:$v") }
            lines += layoutSetting(setting.id, setting.title, pills)
        }
        return lines
    }

    companion object {
        const val KEY_HINTS = "w/s move  a/d option  space/click select  q close"

        /** Title column of a setting row (chars) and where the options column starts. */
        private const val TITLE_COLS = 16
        private const val OPTION_GAP = 4
        private const val OPTIONS_LEFT = -150 + (TITLE_COLS + 1) * UiFont.MONO_ADV + OPTION_GAP

        private const val TAB_GAP = 4

        /** Longest page title drawn in the big font. */
        private const val BIG_TITLE_MAX = 198

        /** Longest option text (the options column is 194 units wide). */
        private const val MAX_PILL_CHARS = 30

        /** Languages per page of the picker (about 4 per row, 10 rows at worst). */
        const val LANGUAGE_PAGE_SIZE = 20

        private const val NET_LABEL_COLS = 12
        private const val NET_VALUE_CHARS = 16
    }
}
