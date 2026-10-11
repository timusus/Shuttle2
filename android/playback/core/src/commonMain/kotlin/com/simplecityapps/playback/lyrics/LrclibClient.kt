package com.simplecityapps.playback.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a song looks like to LRCLIB; [durationSeconds] is how it tells recordings of one title apart. */
data class LyricsQuery(
    val artist: String,
    val title: String,
    val album: String?,
    val durationSeconds: Int,
)

/** One LRCLIB record. [synced] is LRC text with `[mm:ss.xx]` stamps; either field is null when LRCLIB has none. */
data class LrclibLyrics(
    val plain: String?,
    val synced: String?,
    val instrumental: Boolean,
    val durationSeconds: Int?,
) {
    /** Text to show: the plain lyrics, else the synced ones without their stamps. Null for an instrumental or empty record. */
    fun displayText(): String? {
        if (instrumental) return null
        return plain?.takeIf { it.isNotBlank() } ?: synced?.let(::stripTimestamps)?.takeIf { it.isNotBlank() }
    }
}

private val timestamp = Regex("""^\s*(\[\d+:\d+(?:[.:]\d+)?])+\s?""")

internal fun stripTimestamps(lrc: String): String = lrc.lines().joinToString("\n") { it.replace(timestamp, "") }.trim()

/** LRCLIB (https://lrclib.net/docs): free, keyless lyrics by artist, title, album and duration. */
class LrclibClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://lrclib.net/api",
) {
    /**
     * The best record for [query]: `/get`, then `/search` only when `/get` has none. Null when LRCLIB has nothing. Throws
     * on a network or server failure, which isn't the same as having nothing.
     */
    suspend fun find(query: LyricsQuery): LrclibLyrics? = (if (query.durationSeconds > 0) get(query) else null) ?: search(query)

    private suspend fun get(query: LyricsQuery): LrclibLyrics? {
        val response = http.get("$baseUrl/get") {
            parameter("artist_name", query.artist)
            parameter("track_name", query.title)
            query.album?.let { parameter("album_name", it) }
            parameter("duration", query.durationSeconds)
        }
        // LRCLIB answers 400 for a request it can't match on, which /search may still serve
        if (response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.BadRequest) return null
        check(response.status == HttpStatusCode.OK) { "LRCLIB /get: ${response.status}" }
        return parseRecord(Json.parseToJsonElement(response.bodyAsText()).jsonObject)
    }

    private suspend fun search(query: LyricsQuery): LrclibLyrics? {
        val response = http.get("$baseUrl/search") {
            parameter("artist_name", query.artist)
            parameter("track_name", query.title)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status == HttpStatusCode.OK) { "LRCLIB /search: ${response.status}" }
        return parseSearch(response.bodyAsText(), query.durationSeconds)
    }

    companion object {
        const val USER_AGENT = "ShuttleMusic (https://github.com/timusus/Shuttle2)"

        /**
         * An HTTP client that identifies the app to LRCLIB. The platform client already installs a User-Agent, which a
         * per-request header would be sent alongside, so the agent is configured on the client instead.
         */
        fun createHttpClient(preconfiguredClient: Any? = null): HttpClient = com.simplecityapps.networking.createHttpClient(preconfiguredClient = preconfiguredClient) {
            install(UserAgent) { agent = USER_AGENT }
        }

        /** LRCLIB's own matching tolerance for `/get`. */
        private const val DURATION_TOLERANCE_SECONDS = 2

        internal fun parseRecord(json: JsonObject): LrclibLyrics = LrclibLyrics(
            plain = json["plainLyrics"]?.jsonPrimitive?.contentOrNull,
            synced = json["syncedLyrics"]?.jsonPrimitive?.contentOrNull,
            instrumental = json["instrumental"]?.jsonPrimitive?.booleanOrNull ?: false,
            durationSeconds = json["duration"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt(),
        )

        /** The first record that has lyrics and is within tolerance of [durationSeconds]; search results are ranked by relevance. */
        internal fun parseSearch(body: String, durationSeconds: Int): LrclibLyrics? {
            val records = (Json.parseToJsonElement(body) as? JsonArray).orEmpty().map { parseRecord(it.jsonObject) }
            return records.firstOrNull { record ->
                record.displayText() != null &&
                    (durationSeconds <= 0 || record.durationSeconds?.let { kotlin.math.abs(it - durationSeconds) <= DURATION_TOLERANCE_SECONDS } == true)
            }
        }
    }
}
