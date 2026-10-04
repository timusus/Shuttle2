package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/** Jellyfin's library queries: songs, playlists and their items, and a single item. */
class ItemsService(private val client: HttpClient) {
    suspend fun audioItems(
        url: String,
        authorization: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        fields = "Genres,DateCreated,ProviderIds,MediaStreams",
        limit = limit,
        startIndex = startIndex
    )

    suspend fun playlists(
        url: String,
        authorization: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Users/$userId/Items",
        authorization = authorization,
        itemTypes = "Playlist",
        limit = limit,
        startIndex = startIndex
    )

    suspend fun playlistItems(
        url: String,
        authorization: String,
        playlistId: String,
        userId: String,
        limit: Int = 2500,
        startIndex: Int = 0
    ): NetworkResult<QueryResult> = items(
        url = "$url/Playlists/$playlistId/Items",
        authorization = authorization,
        itemTypes = "Audio",
        limit = limit,
        startIndex = startIndex,
        userId = userId
    )

    suspend fun item(
        url: String,
        authorization: String,
        userId: String,
        itemId: String
    ): NetworkResult<Item> = client.networkResult {
        get("$url/Users/$userId/Items/$itemId") {
            header(HttpHeaders.Authorization, authorization)
        }
    }

    private suspend fun items(
        url: String,
        authorization: String,
        itemTypes: String,
        fields: String? = null,
        limit: Int,
        startIndex: Int,
        userId: String? = null
    ): NetworkResult<QueryResult> = client.networkResult {
        get(url) {
            header(HttpHeaders.Authorization, authorization)
            parameter("recursive", true)
            parameter("includeItemTypes", itemTypes)
            parameter("fields", fields)
            parameter("limit", limit)
            parameter("startIndex", startIndex)
            parameter("userId", userId)
        }
    }
}
