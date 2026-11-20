package com.javaniac.blockytype.profile

import com.javaniac.blockytype.ServerConfig
import com.javaniac.blockytype.commands.MojangProfiles
import com.javaniac.blockytype.db.KnownPlayer
import com.javaniac.blockytype.db.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** The result of looking a typed name up. */
sealed interface Lookup {
    /** Not a Minecraft name. */
    data object Invalid : Lookup

    /** A valid name of a player that never joined this server. */
    data object Unknown : Lookup

    data class Found(val player: KnownPlayer) : Lookup
}

/** Finds players and builds their profiles. Only players that joined this server can be looked up. */
object ProfileService {
    /** A stored face is used for a week, then fetched again (players change skins). */
    const val FACE_MAX_AGE_MILLIS = 7L * 24 * 3600 * 1000

    suspend fun lookup(typed: String): Lookup {
        val name = typed.trim()
        if (!MojangProfiles.isValidName(name)) return Lookup.Invalid
        return Storage.playerByName(name)?.let { Lookup.Found(it) } ?: Lookup.Unknown
    }

    /** Downloads a face: from [textureValue] if given (online players), else from Mojang's session server. Blocking. */
    private fun download(uuid: UUID, textureValue: String?): IntArray? {
        val value = textureValue ?: SkinFace.lookupTextureValue(uuid) ?: return null
        return SkinFace.download(value)
    }

    /**
     * The profile of [player]. The face comes from the cache, else from [fetch] (which gets the player's
     * `textures` value when they are online); a missing face is a placeholder that is not stored.
     */
    suspend fun load(
        player: KnownPlayer,
        textureValue: String?,
        admin: Boolean = ServerConfig.isAdmin(player.uuid),
        fetch: (UUID, String?) -> IntArray? = ::download,
    ): ProfileData {
        val results = Storage.profileResults(player.uuid)
        var face = Storage.face(player.uuid, maxAgeMillis = FACE_MAX_AGE_MILLIS)
        if (face == null) {
            face = withContext(Dispatchers.IO) { fetch(player.uuid, textureValue) }
            if (face != null) Storage.saveFace(player.uuid, face)
        }
        return ProfileBuilder.build(player.uuid, player.name, player.firstSeen, results, face ?: SkinFace.PLACEHOLDER, admin)
    }
}
