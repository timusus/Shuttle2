package com.simplecityapps.provider.jellyfin.http

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class AuthenticationResult(
    @Json(name = "User") val user: User,
    @Json(name = "AccessToken") val accessToken: String,
    @Json(name = "ServerId") val serverId: String,
    @Json(name = "SessionInfo") val sessionInfo: SessionInfo
) : Serializable
