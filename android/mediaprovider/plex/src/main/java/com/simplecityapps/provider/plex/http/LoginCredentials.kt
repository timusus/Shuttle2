package com.simplecityapps.provider.plex.http

data class LoginCredentials(
    val username: String,
    val password: String,
    val authCode: String?
) {
    companion object {
        val Empty = LoginCredentials(
            username = "",
            password = "",
            authCode = null
        )
    }
}

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)
