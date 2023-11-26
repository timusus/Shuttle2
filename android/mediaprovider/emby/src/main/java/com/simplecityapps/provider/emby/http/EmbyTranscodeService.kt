package com.simplecityapps.provider.emby.http

import retrofit2.Response
import retrofit2.http.HEAD
import retrofit2.http.Url

interface EmbyTranscodeService {
    @HEAD
    suspend fun transcode(
        @Url url: String
    ): Response<Void>
}
