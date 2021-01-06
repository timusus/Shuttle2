package com.simplecityapps.provider.jellyfin

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaPathProvider
import javax.inject.Inject

class JellyfinMediaPathProvider @Inject constructor(private val jellyfinAuthenticationManager: JellyfinAuthenticationManager) : MediaPathProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "jellyfin"
    }

    override fun getPath(uri: Uri): Uri {
        return jellyfinAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
            jellyfinAuthenticationManager.buildJellyfinPath(
                uri.pathSegments.last(),
                authenticatedCredentials
            )?.toUri() ?: run {
                throw IllegalStateException("Failed to build jellyfin path")
            }
        } ?: run {
            throw IllegalStateException("Failed to authenticate")
        }
    }

    override fun isRemote(uri: Uri): Boolean {
        return true
    }
}