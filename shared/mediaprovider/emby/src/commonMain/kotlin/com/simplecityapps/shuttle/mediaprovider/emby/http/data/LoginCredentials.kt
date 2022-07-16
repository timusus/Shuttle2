package com.simplecityapps.shuttle.mediaprovider.emby.http.data

open class LoginCredentials(
    val username: String,
    val password: String,
)

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)