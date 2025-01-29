package com.simplecityapps.provider.jellyfin.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Url

interface ItemsService {
    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemsImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Query("recursive") recursive: Boolean = true,
        @Query("includeItemTypes") itemTypes: String,
        @Query("fields") fields: String?,
        @Query("limit") limit: Int = 2500,
        @Query("startIndex") startIndex: Int = 0,
        @Query("userId") UserId: String? = null
    ): NetworkResult<QueryResult>

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String
    ): NetworkResult<Item>
}

suspend fun ItemsService.audioItems(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String? = "Genres",
    limit: Int = 2500,
    startIndex: Int = 0
): NetworkResult<QueryResult> = itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)

suspend fun ItemsService.playlists(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Playlist",
    fields: String? = null,
    limit: Int = 2500,
    startIndex: Int = 0
): NetworkResult<QueryResult> = itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields, limit, startIndex)

suspend fun ItemsService.playlistItems(
    url: String,
    token: String,
    playlistId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String? = null,
    limit: Int = 2500,
    startIndex: Int = 0,
    userId: String
): NetworkResult<QueryResult> = itemsImpl("$url/Playlists/$playlistId/Items", token, recursive, itemTypes, fields, limit, startIndex, userId)

suspend fun ItemsService.item(
    url: String,
    token: String,
    userId: String,
    itemId: String
): NetworkResult<Item> = itemImpl("$url/Users/$userId/Items/$itemId", token)
