package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data

open class LoginCredentials(
    val username: String,
    val password: String,
)

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)