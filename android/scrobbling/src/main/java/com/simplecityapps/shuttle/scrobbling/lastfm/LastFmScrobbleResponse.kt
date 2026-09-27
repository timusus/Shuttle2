package com.simplecityapps.shuttle.scrobbling.lastfm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * `track.scrobble`'s response ([show/track.scrobble](https://www.last.fm/api/show/track.scrobble)). A batch
 * request either succeeds, with one [Scrobbles.scrobble] entry per submitted scrobble (accepted or
 * [ScrobbleResult.isIgnored]), or fails with a top-level [error] code and no `scrobbles` at all.
 */
@JsonClass(generateAdapter = true)
data class LastFmScrobbleResponse(
    val scrobbles: Scrobbles? = null,
    val error: Int? = null,
    val message: String? = null
) {
    // Read by ScrobblesJsonAdapterFactory, not @JsonClass codegen: Last.fm serialises a single-element
    // list as a bare object rather than a one-item array, and a custom @JsonQualifier for that tripped up
    // Moshi's KSP codegen on this Kotlin/KSP version, so this one class is parsed by hand instead.
    data class Scrobbles(val scrobble: List<ScrobbleResult> = emptyList())

    @JsonClass(generateAdapter = true)
    data class ScrobbleResult(
        @Json(name = "ignoredMessage") val ignoredMessage: IgnoredMessage? = null
    ) {
        /** A non-zero ignored code is a permanent rejection (bad artist/track/timestamp), never retried. */
        val isIgnored: Boolean
            get() = ignoredMessage != null && ignoredMessage.code != "0"
    }

    @JsonClass(generateAdapter = true)
    data class IgnoredMessage(
        val code: String = "0"
    )

    companion object {
        /** `error` code: the session key is invalid or expired; sign the user out rather than retry. */
        const val ERROR_INVALID_SESSION = 9

        /** `error` codes retried with backoff: rate limited, then the service temporarily unavailable. */
        val RETRYABLE_ERRORS = setOf(11, 16)
    }
}

/** Registered on the Moshi instance built for [LastFmApi]; see [LastFmScrobbleResponse.Scrobbles]. */
class ScrobblesJsonAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (Types.getRawType(type) != LastFmScrobbleResponse.Scrobbles::class.java) return null
        val resultAdapter = moshi.adapter(LastFmScrobbleResponse.ScrobbleResult::class.java)
        return object : JsonAdapter<LastFmScrobbleResponse.Scrobbles>() {
            override fun fromJson(reader: JsonReader): LastFmScrobbleResponse.Scrobbles {
                var results: List<LastFmScrobbleResponse.ScrobbleResult> = emptyList()
                reader.beginObject()
                while (reader.hasNext()) {
                    if (reader.nextName() != "scrobble") {
                        reader.skipValue()
                        continue
                    }
                    results = when (reader.peek()) {
                        JsonReader.Token.BEGIN_ARRAY -> {
                            val list = mutableListOf<LastFmScrobbleResponse.ScrobbleResult>()
                            reader.beginArray()
                            while (reader.hasNext()) list.add(resultAdapter.fromJson(reader) ?: continue)
                            reader.endArray()
                            list
                        }

                        JsonReader.Token.BEGIN_OBJECT -> resultAdapter.fromJson(reader)?.let { listOf(it) } ?: emptyList()

                        JsonReader.Token.NULL -> {
                            reader.nextNull<Any>()
                            emptyList()
                        }

                        else -> {
                            reader.skipValue()
                            emptyList()
                        }
                    }
                }
                reader.endObject()
                return LastFmScrobbleResponse.Scrobbles(results)
            }

            override fun toJson(
                writer: JsonWriter,
                value: LastFmScrobbleResponse.Scrobbles?
            ): Unit = throw UnsupportedOperationException("LastFmScrobbleResponse is only ever read, never written")
        }
    }
}
