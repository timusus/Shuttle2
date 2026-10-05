package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlin.time.Instant

/** The header a Plex server reads its access token from. */
const val PLEX_TOKEN = "X-Plex-Token"

/** A Plex server's library: its sections, a page of a section's tracks, and a single item. */
class ItemsService(private val client: HttpClient) {
    suspend fun items(
        url: String,
        token: String,
        section: String,
        offset: Int,
        limit: Int,
        /** Only the tracks updated on the server (added, or their metadata changed) at or after this time; all of them if null. */
        updatedSince: Instant? = null,
        /** Only the tracks the user rated 5 stars (10), which is what a favourite is on Plex. */
        favouritesOnly: Boolean = false,
        /** Only the tracks the user last played at or after this time. */
        viewedSince: Instant? = null
    ): NetworkResult<QueryResult> = query("$url/library/sections/$section/all", token) {
        parameter("type", 10)
        parameter("includeCollections", 1)
        parameter("includeAdvanced", 1)
        parameter("includeMeta", 1)
        // Adds each track's Guid list, which holds its MusicBrainz recording id
        parameter("includeGuids", 1)
        // Plex's filter syntax: `field>>=value` is "greater than" (as python-plexapi sends it), in epoch seconds
        updatedSince?.let { since -> parameter("updatedAt>>", since.epochSeconds - 1) }
        if (favouritesOnly) parameter("userRating", 10)
        viewedSince?.let { since -> parameter("lastViewedAt>>", since.epochSeconds - 1) }
        // A stable order, so a track is unlikely to slip between pages when the list is paged by offset
        parameter("sort", "addedAt,titleSort")
        parameter("X-Plex-Container-Start", offset)
        parameter("X-Plex-Container-Size", limit)
    }

    /** The server's audio playlists, each with its [Metadata.ratingKey]. */
    suspend fun playlists(
        url: String,
        token: String
    ): NetworkResult<QueryResult> = query("$url/playlists", token) {
        parameter("playlistType", "audio")
    }

    /** A page of a playlist's tracks, in playlist order. */
    suspend fun playlistItems(
        url: String,
        token: String,
        playlist: String,
        offset: Int,
        limit: Int
    ): NetworkResult<QueryResult> = query("$url/playlists/$playlist/items", token) {
        parameter("includeGuids", 1)
        parameter("X-Plex-Container-Start", offset)
        parameter("X-Plex-Container-Size", limit)
    }

    suspend fun sections(
        url: String,
        token: String
    ): NetworkResult<QueryResult> = query("$url/library/sections", token)

    suspend fun item(
        url: String,
        token: String,
        key: String
    ): NetworkResult<QueryResult> = query("$url$key", token)

    /** The full metadata, Streams included, of the tracks with the given [ratingKeys] in one request (a listing has no Streams). */
    suspend fun metadata(
        url: String,
        token: String,
        ratingKeys: List<String>
    ): NetworkResult<QueryResult> = query("$url/library/metadata/${ratingKeys.joinToString(",")}", token)

    private suspend fun query(
        url: String,
        token: String,
        block: HttpRequestBuilder.() -> Unit = {}
    ): NetworkResult<QueryResult> = client.networkResult {
        get(url) {
            header(PLEX_TOKEN, token)
            block()
        }
    }
}
