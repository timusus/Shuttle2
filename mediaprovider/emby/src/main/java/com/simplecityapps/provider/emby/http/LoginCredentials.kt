package com.simplecityapps.provider.emby.http

open class LoginCredentials(
    val username: String,
    val password: String,
)

class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String
)