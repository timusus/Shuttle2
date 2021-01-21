package com.simplecityapps.provider.plex

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaPathProvider
import javax.inject.Inject

class PlexMediaPathProvider @Inject constructor(private val plexAuthenticationManager: PlexAuthenticationManager) : MediaPathProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "plex"
    }

    override fun getPath(uri: Uri): Uri {
        return plexAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
            plexAuthenticationManager.buildPlexPath(
                uri,
                authenticatedCredentials
            )?.toUri() ?: run {
                throw IllegalStateException("Failed to build plex path")
            }
        } ?: run {
            throw IllegalStateException("Failed to authenticate")
        }
    }

    override fun isRemote(uri: Uri): Boolean {
        return true
    }
}