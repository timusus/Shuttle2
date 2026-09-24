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

data class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String,
    /** The user's `Policy.EnableContentDownloading`, refreshed whenever the session is validated; chooses the download URL. */
    val canDownload: Boolean = false
)
