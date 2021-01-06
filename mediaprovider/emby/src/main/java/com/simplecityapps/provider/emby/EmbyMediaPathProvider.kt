package com.simplecityapps.provider.emby

import android.net.Uri
import androidx.core.net.toUri
import com.simplecityapps.mediaprovider.MediaPathProvider
import javax.inject.Inject

class EmbyMediaPathProvider @Inject constructor(private val embyAuthenticationManager: EmbyAuthenticationManager) : MediaPathProvider {

    override fun handles(uri: Uri): Boolean {
        return uri.scheme == "emby"
    }

    override fun getPath(uri: Uri): Uri {
        return embyAuthenticationManager.getAuthenticatedCredentials()?.let { authenticatedCredentials ->
            embyAuthenticationManager.buildEmbyPath(
                uri.pathSegments.last(),
                authenticatedCredentials
            )?.toUri() ?: run {
                throw IllegalStateException("Failed to build emby path")
            }
        } ?: run {
            throw IllegalStateException("Failed to authenticate")
        }
    }

    override fun isRemote(uri: Uri): Boolean {
        return true
    }
}