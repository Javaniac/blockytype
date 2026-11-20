package com.javaniac.blockytype.game

import com.javaniac.blockytype.commands.SettingsChat
import com.javaniac.blockytype.settings.SettingsCatalog

/**
 * Server side completions for chat commands (`/settings`, admin `/pack`). The client only asks for these
 * through `ask_server` nodes of [TypingCommandTree]; the answer is a `TabCompletePacket`.
 */
object CommandSuggest {
    /** The client shows ~10 rows; more entries only cost bandwidth. */
    const val MAX = 100

    /** [start] and [length] are offsets in the full text including the leading `/`. */
    class Result(val start: Int, val length: Int, val matches: List<String>)

    private val PACK_FIRST = listOf("reload", "font", "input")
    private val PACK_FONT = listOf("list", "download", "use", "reset")
    private val PACK_INPUT = listOf("show", "hide")

    fun suggest(
        text: String, isAdmin: Boolean, choices: (String) -> List<String>, fonts: List<String>,
        players: () -> Collection<String> = { emptyList() },
    ): Result? {
        if (text.startsWith("/settings ")) return settings(text, choices)
        if (text.startsWith("/profile ")) return ProfileSuggest.answer(text, "/profile ", players())
        if (isAdmin && text.startsWith("/pack ")) return pack(text, fonts)
        return null
    }

    /** Like [suggest], but text without completions gets an empty answer: the client waits for every ask_server node. */
    fun answer(
        text: String, isAdmin: Boolean, choices: (String) -> List<String>, fonts: List<String>,
        players: () -> Collection<String> = { emptyList() },
    ): Result = suggest(text, isAdmin, choices, fonts, players) ?: Result(text.length, 0, emptyList())

    private fun settings(text: String, choices: (String) -> List<String>): Result? {
        val base = "/settings ".length
        val rest = text.substring(base)
        val space = rest.indexOf(' ')
        if (space < 0) return complete(base, rest, SettingsChat.ids() + "reset")
        val setting = SettingsCatalog.byId(rest.substring(0, space))?.takeIf { it.id in SettingsChat.ids() } ?: return null
        val values = choices(setting.id) + if (setting.serverDefault) listOf("default") else emptyList()
        return complete(base + space + 1, rest.substring(space + 1), values.distinct())
    }

    private fun pack(text: String, fonts: List<String>): Result? {
        val base = "/pack ".length
        val words = text.substring(base).split(' ')
        val start = text.length - words.last().length
        val options = when {
            words.size == 1 -> PACK_FIRST
            words.size == 2 && words[0] == "font" -> PACK_FONT
            words.size == 2 && words[0] == "input" -> PACK_INPUT
            words.size == 3 && words[0] == "font" && words[1] == "use" -> fonts
            else -> return null
        }
        return complete(start, words.last(), options)
    }

    private fun complete(start: Int, typed: String, options: List<String>): Result =
        Result(start, typed.length, options.filter { it.startsWith(typed, ignoreCase = true) }.take(MAX))
}
