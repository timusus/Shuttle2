package com.simplecityapps.provider.jellyfin.http

import retrofit2.Response
import retrofit2.http.HEAD
import retrofit2.http.Url

interface JellyfinTranscodeService {

    @HEAD
    suspend fun transcode(@Url url: String): Response<Void>
}
