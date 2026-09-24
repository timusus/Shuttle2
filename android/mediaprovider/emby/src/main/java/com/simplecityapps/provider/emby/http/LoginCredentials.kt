package com.simplecityapps.provider.emby.http

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
    val userId: String,
    /** The user's `Policy.EnableContentDownloading`, captured at sign-in; chooses the download URL. */
    val canDownload: Boolean = false
)
