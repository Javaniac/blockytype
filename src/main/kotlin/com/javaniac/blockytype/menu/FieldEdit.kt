package com.javaniac.blockytype.menu

import com.javaniac.blockytype.NetworkSettings
import com.javaniac.blockytype.commands.MojangProfiles

/**
 * A text value being typed into the command block screen: an admin network setting, or the name of a player whose
 * profile is looked up ([lookup]).
 */
class FieldEdit private constructor(val key: String, initial: String, val isLookup: Boolean) {
    /** An admin network setting; [key] must be one of [NetworkSettings.KEYS]. */
    constructor(key: String, initial: String) : this(key, initial, false) {
        require(key in NetworkSettings.KEYS) { "not an editable field: $key" }
    }

    var draft: String = initial.take(MAX_DRAFT)
        private set

    /** Why the last [commit] failed, or null. */
    var error: String? = null
        private set

    /** The player typed [text] (everything after the `/t ` prefix). */
    fun type(text: String) {
        draft = text.take(MAX_DRAFT)
        error = null
    }

    /** Marks the edit as failed for a reason only the caller knows (a name nobody has) and shows it. */
    fun fail(message: String) {
        error = message
    }

    /** The valid value of [text] (the final box text), or null with [error] set. */
    fun commit(text: String = draft): String? {
        draft = text.take(MAX_DRAFT)
        val value = if (isLookup) draft.trim().takeIf { MojangProfiles.isValidName(it) } else NetworkSettings.validate(key, draft)
        error = if (value == null) (if (isLookup) "not a valid name" else "invalid value") else null
        return value
    }

    companion object {
        const val MAX_DRAFT = 120
        const val LOOKUP_KEY = "profile-lookup"

        /** Typing the name of a player. */
        fun lookup(): FieldEdit = FieldEdit(LOOKUP_KEY, "", true)
    }
}
