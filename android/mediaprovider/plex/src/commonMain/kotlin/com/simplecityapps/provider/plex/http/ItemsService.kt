package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/** The header a Plex server reads its access token from. */
const val PLEX_TOKEN = "X-Plex-Token"

/** A Plex server's library: its sections, a page of a section's tracks, and a single item. */
class ItemsService(private val client: HttpClient) {
    suspend fun items(
        url: String,
        token: String,
        section: String,
        offset: Int,
        limit: Int
    ): NetworkResult<QueryResult> = query("$url/library/sections/$section/all", token) {
        parameter("type", 10)
        parameter("includeCollections", 1)
        parameter("includeAdvanced", 1)
        parameter("includeMeta", 1)
        // Adds each track's Guid list, which holds its MusicBrainz recording id
        parameter("includeGuids", 1)
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
