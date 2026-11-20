package com.javaniac.blockytype.game

/** Completion of player names for the profile look up (online players only: offline names are not listed). */
object ProfileSuggest {
    /** The client shows ~10 rows; more entries only cost bandwidth. */
    const val MAX = 20

    /** Names of [online] starting with [typed] (any case), sorted without case, at most [MAX]. */
    fun names(typed: String, online: Collection<String>): List<String> =
        online.distinct().filter { it.startsWith(typed, ignoreCase = true) }.sortedBy { it.lowercase() }.take(MAX)

    /** The answer to a completion request for [text], where [prefix] (`/t ` or `/profile `) comes before the name. */
    fun answer(text: String, prefix: String, online: Collection<String>): CommandSuggest.Result {
        val typed = text.removePrefix(prefix)
        return CommandSuggest.Result(prefix.length, typed.length, names(typed, online))
    }
}
