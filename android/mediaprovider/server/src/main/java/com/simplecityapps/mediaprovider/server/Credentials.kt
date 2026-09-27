package com.simplecityapps.mediaprovider.server

/** What the user signs in to a media server with. */
data class LoginCredentials(
    val username: String,
    val password: String,
    /** Plex's two-factor code, used for the sign-in request only and never stored. */
    val authCode: String? = null
) {
    companion object {
        val Empty = LoginCredentials(
            username = "",
            password = ""
        )
    }
}

/** A signed-in session: the token requests are made with and the user it belongs to. */
data class AuthenticatedCredentials(
    val accessToken: String,
    val userId: String,
    /** Jellyfin and Emby's `Policy.EnableContentDownloading`, refreshed whenever the session is validated; chooses the download URL. */
    val canDownload: Boolean = false
)
