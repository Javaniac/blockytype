package com.javaniac.blockytype.commands

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

data class Profile(val uuid: UUID, val name: String)

/** Username to UUID lookup through Mojang's public profile API. Blocking, call from IO. */
object MojangProfiles {
    private val NAME = Regex("[A-Za-z0-9_]{3,16}")
    private val ID = Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"")
    private val PROFILE_NAME = Regex("\"name\"\\s*:\\s*\"([A-Za-z0-9_]{1,16})\"")

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun isValidName(name: String) = NAME.matches(name)

    fun parse(json: String): Profile? {
        val id = ID.find(json)?.groupValues?.get(1) ?: return null
        val name = PROFILE_NAME.find(json)?.groupValues?.get(1) ?: return null
        val dashed = "${id.substring(0, 8)}-${id.substring(8, 12)}-${id.substring(12, 16)}-${id.substring(16, 20)}-${id.substring(20)}"
        return Profile(UUID.fromString(dashed), name)
    }

    /** The profile of [uuid] from the session server, or null when Mojang does not know it. Throws on network errors. */
    fun byUuid(uuid: UUID): Profile? {
        val request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/${uuid.toString().replace("-", "")}"))
            .timeout(Duration.ofSeconds(8)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> parse(response.body())
            204, 404 -> null
            else -> error("Mojang session server answered HTTP ${response.statusCode()}")
        }
    }

    /** Null if the name does not exist. Throws on network errors. */
    fun lookup(name: String): Profile? {
        require(isValidName(name)) { "Invalid username '$name'" }
        val request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/$name"))
            .timeout(Duration.ofSeconds(8)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            200 -> parse(response.body())
            404 -> null
            else -> error("Mojang API answered HTTP ${response.statusCode()}")
        }
    }
}
