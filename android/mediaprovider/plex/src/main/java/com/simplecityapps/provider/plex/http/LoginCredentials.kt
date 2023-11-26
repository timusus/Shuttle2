package com.simplecityapps.provider.plex.http

open class LoginCredentials(
    val username: String,
    val password: String,
    val authCode: String?
)

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)
