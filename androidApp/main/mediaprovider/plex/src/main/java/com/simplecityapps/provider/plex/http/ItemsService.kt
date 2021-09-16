package com.simplecityapps.provider.plex.http

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Url

interface ItemsService {

    @GET
    @Headers(
        "Accept: application/json",
        "Content-Type: application/json"
    )
    suspend fun itemsImpl(
        @Url url: String,
        @Header("X-Plex-Token") token: String
    ): NetworkResult<QueryResult>
}

suspend fun ItemsService.items(
    url: String,
    token: String,
): NetworkResult<QueryResult> {
    return itemsImpl(
        url = "$url/library/sections/1/all" +
                "?type=10" +
                "&includeCollections=1" +
                "&includeAdvanced=1" +
                "&includeMeta=1",
        token = token
    )
}