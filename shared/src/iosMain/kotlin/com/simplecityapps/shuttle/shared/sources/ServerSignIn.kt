package com.simplecityapps.shuttle.shared.sources

import com.simplecityapps.mediaprovider.server.LoginCredentials
import com.simplecityapps.networking.userDescription
import com.simplecityapps.provider.emby.EmbyAuthenticationManager
import com.simplecityapps.provider.jellyfin.JellyfinAuthenticationManager
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.screens.sources.ConnectServer
import dev.zacsweers.metro.Inject

/**
 * Signs in to a Jellyfin or Emby server from Swift, the iOS stand-in for Android's `SignInToServer` until
 * `ServerSignInView` lands (phase 7): the address is saved, the provider's authentication manager signs in, and
 * [ConnectServer] (what the server-type picker runs after a sign-in) enables the provider and starts an import.
 * Only the session token is kept, in the Keychain; the password is never stored or logged.
 */
@Inject
class ServerSignIn(
    private val jellyfin: JellyfinAuthenticationManager,
    private val emby: EmbyAuthenticationManager,
    private val connectServer: ConnectServer,
) {
    /** Null once signed in, else what went wrong, for the user. */
    suspend fun signIn(type: MediaProviderType, address: String, username: String, password: String): String? {
        val credentials = LoginCredentials(username, password)
        val result = when (type) {
            MediaProviderType.Jellyfin -> {
                jellyfin.setAddress(address)
                jellyfin.authenticate(address, credentials).map {}
            }

            MediaProviderType.Emby -> {
                emby.setAddress(address)
                emby.authenticate(address, credentials).map {}
            }

            else -> return "$type sign-in isn't available on iOS"
        }
        return result.fold(
            onSuccess = {
                connectServer(type)
                null
            },
            onFailure = { it.userDescription() },
        )
    }

    /** Whether a session for [type] at [address] is already saved, so launching again needn't sign in. */
    fun isSignedIn(type: MediaProviderType, address: String): Boolean = when (type) {
        MediaProviderType.Jellyfin -> jellyfin.getAddress() == address && jellyfin.getAuthenticatedCredentials() != null
        MediaProviderType.Emby -> emby.getAddress() == address && emby.getAuthenticatedCredentials() != null
        else -> false
    }
}
