package com.simplecityapps.shuttle.mediaprovider.emby.http.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationResponse(
    @SerialName("User") val user: UserResponse,
    @SerialName("AccessToken") val accessToken: String,
    @SerialName("ServerId") val serverId: String,
    @SerialName("SessionInfo") val sessionInfo: SessionInfoResponse
)