package com.simplecityapps.provider.jellyfin.http

data class LoginCredentials(
    val username: String,
    val password: String
) {
    companion object {
        val Empty = LoginCredentials(
            username = "",
            password = ""
        )
    }
}

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)
