package com.simplecityapps.provider.emby.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.*

interface ItemsService {

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemsImpl(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") itemTypes: String = "Audio",
        @Query("Fields") fields: String = "Genres"
    ): NetworkResult<QueryResult>
}

suspend fun ItemsService.items(
    url: String,
    token: String,
    userId: String,
    recursive: Boolean = true,
    itemTypes: String = "Audio",
    fields: String = "Genres",
): NetworkResult<QueryResult> {
    return itemsImpl("$url/Users/$userId/Items", token, recursive, itemTypes, fields)
}