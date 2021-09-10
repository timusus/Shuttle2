package com.simplecityapps.provider.plex.http

import retrofit2.Response
import retrofit2.http.HEAD
import retrofit2.http.Url

interface PlexTranscodeService {

    @HEAD
    suspend fun transcode(@Url url: String): Response<Void>
}