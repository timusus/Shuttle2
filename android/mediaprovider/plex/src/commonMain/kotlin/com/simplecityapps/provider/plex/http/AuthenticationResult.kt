package com.simplecityapps.provider.plex.http

import kotlinx.serialization.Serializable

/** plex.tv's sign-in response. */
@Serializable
data class AuthenticationResult(
    val user: User
)
