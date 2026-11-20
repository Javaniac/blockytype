package com.javaniac.blockytype.menu

import com.javaniac.blockytype.commands.AdminTag
import com.javaniac.blockytype.profile.Best
import com.javaniac.blockytype.profile.ProfileBuilder
import com.javaniac.blockytype.profile.ProfileData
import com.javaniac.blockytype.profile.ProfileFormat
import com.javaniac.blockytype.profile.ResultRow
import com.javaniac.blockytype.ui.Cell
import com.javaniac.blockytype.ui.FaceArt
import com.javaniac.blockytype.ui.MenuRow
import com.javaniac.blockytype.ui.Role
import com.javaniac.blockytype.ui.UiFont

/** What the profile page has of a player: still being loaded, there, or no such player. */
sealed interface ProfileView {
    data object Loading : ProfileView

    data object Missing : ProfileView

    class Ready(val data: ProfileData) : ProfileView
}

/** A row of the profile page and the hint the footer shows while it is focused. */
class ProfileLine(val row: MenuRow, val hint: String? = null)

/**
 * The rows of the profile page (like Monkeytype's profile): face, name, join date and level, totals, the best test
 * of each standard mode and the recent tests, [RECENT_PER_PAGE] at a time. Everything sits on fixed columns, so
 * a long name or a big number is cut instead of running into its neighbour.
 */
object ProfileRows {
    const val RECENT_PER_PAGE = 5
    private const val BAR_CHARS = 20
    private const val ADV = UiFont.MONO_ADV
    private const val LEFT = -150
    private const val TEXT_LEFT = LEFT + UiFont.FACE_PIXEL * 8 + 2 * ADV - 4 // right of the face
    private const val PB_STEP = 75

    private val PB_LABELS = mapOf(
        "time 15" to "15s", "time 30" to "30s", "time 60" to "60s", "time 120" to "2m",
        "words 10" to "10w", "words 25" to "25w", "words 50" to "50w", "words 100" to "100w",
    )

    const val LOOKUP_HINT = "select look up, then type a name  tab completes online players  enter opens it"

    private fun fit(text: String, chars: Int) = if (text.length <= chars) text else text.take(chars)

    /** Cuts decimals when [text] does not fit [chars]. */
    private fun short(text: String, chars: Int): String {
        if (text.length <= chars) return text
        val suffix = if (text.endsWith("%")) "%" else ""
        return fit(text.substringBefore('.') + suffix, chars)
    }

    private fun cell(text: String, x: Int, chars: Int, role: Role) = Cell(fit(text, chars), role, at = x)

    private fun row(vararg cells: Cell, face: FaceArt? = null) = MenuRow(cells.toList(), LEFT, gap = 0, face = face)

    private fun lookupPill(editing: FieldEdit?, x: Int, fromRight: Boolean): Cell {
        val shown = editing?.takeIf { it.isLookup }
        val cell = if (shown != null) {
            Cell(fit(shown.draft, 11) + "_", Role.BG, "lookup", fill = Role.MAIN, pad = 1)
        } else {
            Cell("look up", Role.TEXT, "lookup", fill = Role.SUB_ALT, pad = 1)
        }
        return cell.copy(at = if (fromRight) x - cell.width else x)
    }

    /** The rows below the tabs (there are none on this page): at most [com.javaniac.blockytype.ui.MenuLayout.BODY_ROWS]. */
    fun lines(
        view: ProfileView,
        own: Boolean,
        recentPage: Int,
        now: Long,
        speed: (Double) -> String,
        percent: (Double) -> String,
        editing: FieldEdit?,
    ): List<ProfileLine> = when (view) {
        ProfileView.Loading -> listOf(ProfileLine(row(cell("loading profile...", LEFT, 40, Role.SUB))))
        ProfileView.Missing -> listOf(
            ProfileLine(row(cell("no such player", LEFT, 40, Role.ERROR))),
            ProfileLine(MenuRow(listOf(lookupPill(editing, 150, fromRight = true)), LEFT, gap = 0), LOOKUP_HINT),
        )
        is ProfileView.Ready -> ready(view.data, own, recentPage, now, speed, percent, editing)
    }

    private fun ready(
        d: ProfileData, own: Boolean, recentPage: Int, now: Long,
        speed: (Double) -> String, percent: (Double) -> String, editing: FieldEdit?,
    ): List<ProfileLine> {
        val lines = mutableListOf<ProfileLine>()
        val faceArt = d.face?.let { FaceArt(LEFT, it) }
        lines += ProfileLine(
            if (d.admin) {
                row(Cell(AdminTag.TEXT, Role.MAIN, at = TEXT_LEFT), Cell(" " + fit(d.name, 16), Role.TEXT), face = faceArt)
            } else {
                row(cell(d.name, TEXT_LEFT, 18, Role.MAIN), face = faceArt)
            }
        )
        lines += ProfileLine(row(cell("joined " + (d.joined?.let(ProfileFormat::date) ?: "unknown"), TEXT_LEFT, 22, Role.SUB)))
        lines += ProfileLine(levelRow(d))

        // totals and buttons
        val completed = if (d.completed < 1_000_000) d.completed.toString() else ProfileFormat.compact(d.completed.toLong())
        val buttons = mutableListOf<Cell>()
        var right = 150
        if (!own) {
            val me = Cell("my profile", Role.TEXT, "profile:self", fill = Role.SUB_ALT, pad = 1)
            buttons += me.copy(at = right - me.width)
            right -= me.width + 4
        }
        buttons += lookupPill(editing, right, fromRight = true)
        val totals = listOf(
            Cell(completed, Role.MAIN, at = LEFT), Cell(" tests ", Role.SUB), Cell(ProfileFormat.duration(d.seconds), Role.MAIN),
        )
        lines += ProfileLine(MenuRow(totals + buttons.reversed(), LEFT, gap = 0), LOOKUP_HINT)

        // personal bests: time modes, then word modes
        for (group in ProfileBuilder.PB_MODES.chunked(4)) lines += ProfileLine(bestsRow(group, d.bests, speed, percent))

        // recent tests
        val pages = (d.recent.size + RECENT_PER_PAGE - 1) / RECENT_PER_PAGE
        val page = recentPage.coerceIn(0, (pages - 1).coerceAtLeast(0))
        lines += ProfileLine(recentHeader(page, pages))
        val shown = d.recent.drop(page * RECENT_PER_PAGE).take(RECENT_PER_PAGE)
        if (shown.isEmpty()) lines += ProfileLine(row(cell("no tests yet", LEFT, 20, Role.SUB)))
        for (r in shown) lines += ProfileLine(recentRow(r, now, speed, percent))
        return lines
    }

    private fun levelRow(d: ProfileData): MenuRow {
        val l = d.level
        val filled = (l.into.toDouble() / l.toNext * BAR_CHARS).toInt().coerceIn(0, BAR_CHARS)
        val barLeft = TEXT_LEFT + 8 * ADV
        val cells = mutableListOf(Cell(fit("lvl ${l.level}", 7), Role.MAIN, at = TEXT_LEFT))
        if (filled > 0) cells += Cell(" ".repeat(filled), Role.TEXT, fill = Role.MAIN, at = barLeft)
        if (filled < BAR_CHARS) {
            cells += Cell(" ".repeat(BAR_CHARS - filled), Role.TEXT, fill = Role.SUB_ALT, at = if (filled > 0) null else barLeft)
        }
        cells += Cell(" " + fit("${ProfileFormat.compact(l.into)}/${ProfileFormat.compact(l.toNext)}", 9), Role.SUB)
        return MenuRow(cells, LEFT, gap = 0)
    }

    private fun bestsRow(modes: List<String>, bests: Map<String, Best>, speed: (Double) -> String, percent: (Double) -> String): MenuRow {
        val cells = mutableListOf<Cell>()
        modes.forEachIndexed { i, mode ->
            val x = LEFT + i * PB_STEP
            cells += Cell(PB_LABELS.getValue(mode), Role.SUB, at = x)
            val best = bests[mode]
            if (best == null) {
                cells += Cell("-", Role.SUB, at = x + 4 * ADV)
            } else {
                cells += Cell(short(speed(best.wpm), 4), Role.MAIN, at = x + 4 * ADV)
                cells += Cell(short(percent(best.accuracy), 4), Role.SUB, at = x + 8 * ADV)
            }
        }
        return MenuRow(cells, LEFT, gap = 0)
    }

    private fun recentHeader(page: Int, pages: Int): MenuRow {
        val cells = mutableListOf(Cell("recent tests", Role.SUB, at = LEFT))
        if (pages > 1) {
            val next = Cell("next", Role.TEXT, if (page < pages - 1) "profile-recent:${page + 1}" else null, fill = Role.SUB_ALT, pad = 1)
            val label = Cell("${page + 1}/$pages", Role.SUB)
            val prev = Cell("prev", Role.TEXT, if (page > 0) "profile-recent:${page - 1}" else null, fill = Role.SUB_ALT, pad = 1)
            val nextX = 150 - next.width
            val labelX = nextX - 4 - label.width
            cells += prev.copy(at = labelX - 4 - prev.width)
            cells += label.copy(at = labelX)
            cells += next.copy(at = nextX)
        }
        return MenuRow(cells, LEFT, gap = 0)
    }

    private fun recentRow(r: ResultRow, now: Long, speed: (Double) -> String, percent: (Double) -> String): MenuRow = MenuRow(
        listOf(
            cell(ProfileBuilder.modeKey(r.mode), LEFT, 9, Role.SUB),
            cell(speed(r.wpm), LEFT + 10 * ADV, 7, Role.MAIN),
            cell(percent(r.accuracy), LEFT + 17 * ADV, 6, Role.TEXT),
            cell("raw " + speed(r.raw), LEFT + 23 * ADV, 8, Role.SUB),
            cell(percent(r.consistency) + " cons", LEFT + 31 * ADV, 8, Role.SUB),
            cell(ProfileFormat.ago(now, r.at), LEFT + 41 * ADV, 4, Role.SUB),
        ),
        LEFT, gap = 0,
    )
}
