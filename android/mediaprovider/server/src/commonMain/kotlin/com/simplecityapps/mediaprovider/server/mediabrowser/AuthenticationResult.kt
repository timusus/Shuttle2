package com.simplecityapps.mediaprovider.server.mediabrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/Users/AuthenticateByName` and `/Users/AuthenticateWithQuickConnect`'s answer: the session's token and its user. */
@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: User,
    @SerialName("AccessToken") val accessToken: String
)
