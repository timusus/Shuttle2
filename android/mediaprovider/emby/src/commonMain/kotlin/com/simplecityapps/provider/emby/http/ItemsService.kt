package com.simplecityapps.provider.emby.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/** Emby's library queries: songs, playlists and their items, and a single item. */
class ItemsService(private val client: HttpClient) {
    suspend fun audioItems(
        url: String,
        token: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        token = token,
        itemTypes = "Audio",
        fields = "Genres,ProductionYear,DateCreated",
        limit = limit,
        startIndex = startIndex
    )

    suspend fun playlists(
        url: String,
        token: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        token = token,
        itemTypes = "Playlist",
        limit = limit,
        startIndex = startIndex
    )

    suspend fun playlistItems(
        url: String,
        token: String,
        playlistId: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Playlists/$playlistId/Items",
        token = token,
        itemTypes = "Audio",
        limit = limit,
        startIndex = startIndex,
        userId = userId
    )

    suspend fun item(
        url: String,
        token: String,
        userId: String,
        itemId: String
    ): NetworkResult<Item> = client.networkResult {
        get("$url/Users/$userId/Items/$itemId") {
            header(EMBY_TOKEN, token)
        }
    }

    private suspend fun items(
        url: String,
        token: String,
        itemTypes: String,
        fields: String? = null,
        limit: Int,
        startIndex: Int,
        userId: String? = null
    ): NetworkResult<QueryResult> = client.networkResult {
        get(url) {
            header(EMBY_TOKEN, token)
            parameter("Recursive", true)
            parameter("IncludeItemTypes", itemTypes)
            parameter("Fields", fields)
            parameter("Limit", limit)
            parameter("StartIndex", startIndex)
            parameter("UserId", userId)
        }
    }
}
