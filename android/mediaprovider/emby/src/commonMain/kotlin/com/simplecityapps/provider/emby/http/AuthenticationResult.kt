package com.simplecityapps.provider.emby.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: User,
    @SerialName("AccessToken") val accessToken: String
)
