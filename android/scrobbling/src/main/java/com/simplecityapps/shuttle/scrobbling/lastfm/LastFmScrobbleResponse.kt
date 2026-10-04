package com.simplecityapps.shuttle.scrobbling.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonTransformingSerializer

/**
 * `track.scrobble`'s response ([show/track.scrobble](https://www.last.fm/api/show/track.scrobble)). A batch
 * request either succeeds, with one [Scrobbles.scrobble] entry per submitted scrobble (accepted or
 * ignored), or fails with a top-level [error] code and no `scrobbles` at all.
 */
@Serializable
data class LastFmScrobbleResponse(
    val scrobbles: Scrobbles? = null,
    val error: Int? = null,
    val message: String? = null
) {
    // Last.fm serialises a single-element list as a bare object rather than a one-item array;
    // ScrobbleListSerializer normalises both shapes (and a null) to a list.
    @Serializable
    data class Scrobbles(
        @Serializable(with = ScrobbleListSerializer::class)
        val scrobble: List<ScrobbleResult> = emptyList()
    )

    @Serializable
    data class ScrobbleResult(
        @SerialName("ignoredMessage") val ignoredMessage: IgnoredMessage? = null
    )

    @Serializable
    data class IgnoredMessage(
        val code: String = "0"
    )
}

/** Normalises Last.fm's bare-object-or-array `scrobble` field into a list; see [LastFmScrobbleResponse.Scrobbles]. */
object ScrobbleListSerializer : JsonTransformingSerializer<List<LastFmScrobbleResponse.ScrobbleResult>>(
    ListSerializer(LastFmScrobbleResponse.ScrobbleResult.serializer())
) {
    override fun transformDeserialize(element: JsonElement) = when (element) {
        is JsonArray -> element
        is JsonNull -> JsonArray(emptyList())
        else -> JsonArray(listOf(element))
    }
}
