package com.simplecityapps.provider.plex.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class AuthenticationResult(
    @Json(name = "user") val user: User,
) : Serializable