package com.javaniac.blockytype.profile

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** One saved test of a player, as the profile needs it. [at] is epoch milliseconds. */
data class ResultRow(
    val mode: String,
    val wpm: Double,
    val raw: Double,
    val accuracy: Double,
    val consistency: Double,
    val seconds: Double,
    val at: Long,
)

/** The best test of a mode. */
data class Best(val wpm: Double, val raw: Double, val accuracy: Double, val consistency: Double, val at: Long)

/** Level of a player and the progress inside it: [into] of [toNext] xp. */
data class LevelInfo(val level: Int, val into: Long, val toNext: Long, val xp: Long)

/** Everything a profile page shows. [face] is the 8 x 8 face as 64 `0xRRGGBB` values, row by row, or null. */
class ProfileData(
    val uuid: UUID,
    val name: String,
    val joined: Long?,
    val completed: Int,
    val seconds: Double,
    val level: LevelInfo,
    val bests: Map<String, Best>,
    /** Newest first. */
    val recent: List<ResultRow>,
    val face: IntArray?,
    /** The player has administrator permissions (their profile carries the `[Server Admin]` tag). */
    val admin: Boolean = false,
)

/** BlockyType's own levels: every test gives xp for its length and accuracy, each level needs a bit more. */
object Level {
    /** Xp of one test: a base plus one per second typed, scaled by accuracy. */
    fun xp(r: ResultRow): Long = 10 + Math.round(r.seconds * r.accuracy / 100.0)

    /** Xp needed to get from [level] to the next one. */
    fun toNext(level: Int): Long = 100L + 25L * (level - 1)

    /** Total xp needed to reach [level] (level 1 needs none). */
    fun totalFor(level: Int): Long {
        val n = (level - 1).toLong()
        return 100L * n + 25L * n * (n - 1) / 2
    }

    fun of(xp: Long): LevelInfo {
        var level = 1
        var left = xp.coerceAtLeast(0)
        while (left >= toNext(level)) {
            left -= toNext(level)
            level++
        }
        return LevelInfo(level, left, toNext(level), xp.coerceAtLeast(0))
    }
}

object ProfileBuilder {
    /** The modes of the personal best tiles, in display order (like Monkeytype's profile). */
    val PB_MODES = listOf("time 15", "time 30", "time 60", "time 120", "words 10", "words 25", "words 50", "words 100")

    /** `words 25 english` to `words 25`. */
    fun modeKey(stored: String): String = stored.split(' ').take(2).joinToString(" ")

    /** [results] are in any order; the best tests are the highest wpm of each mode (the earliest one wins a tie). */
    fun build(
        uuid: UUID, name: String, joined: Long?, results: List<ResultRow>, face: IntArray?, admin: Boolean = false,
    ): ProfileData {
        val bests = HashMap<String, Best>()
        for (r in results.sortedBy { it.at }) {
            val key = modeKey(r.mode)
            if (key !in PB_MODES) continue
            val old = bests[key]
            if (old == null || r.wpm > old.wpm) bests[key] = Best(r.wpm, r.raw, r.accuracy, r.consistency, r.at)
        }
        return ProfileData(
            uuid = uuid, name = name, joined = joined,
            completed = results.size,
            seconds = results.sumOf { it.seconds },
            level = Level.of(results.sumOf { Level.xp(it) }),
            bests = PB_MODES.mapNotNull { k -> bests[k]?.let { k to it } }.toMap(),
            recent = results.sortedByDescending { it.at },
            face = face,
            admin = admin,
        )
    }
}

object ProfileFormat {
    private val DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)

    /** `28:31:18`: hours, minutes and seconds, hours may have more than two digits. */
    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        return "%02d:%02d:%02d".format(Locale.ROOT, total / 3600, total / 60 % 60, total % 60)
    }

    fun date(epochMillis: Long): String = DATE.format(Instant.ofEpochMilli(epochMillis))

    /** A short age: `now`, `5m`, `3h`, `2d`, `3w`, `4mo`, `2y`. A time in the future counts as now. */
    fun ago(now: Long, then: Long): String {
        val s = ((now - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "now"
            s < 3600 -> "${s / 60}m"
            s < 86_400 -> "${s / 3600}h"
            s < 7 * 86_400 -> "${s / 86_400}d"
            s < 30 * 86_400 -> "${s / (7 * 86_400)}w"
            s < 365 * 86_400 -> "${s / (30 * 86_400)}mo"
            else -> "${s / (365 * 86_400)}y"
        }
    }

    /** `999`, `5.0k`, `12k`, `1.2m`. */
    fun compact(n: Long): String = when {
        n < 1000 -> n.toString()
        n < 10_000 -> "%.1fk".format(Locale.ROOT, n / 1000.0)
        n < 1_000_000 -> "${n / 1000}k"
        else -> "%.1fm".format(Locale.ROOT, n / 1_000_000.0)
    }
}
