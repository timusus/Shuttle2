package com.simplecityapps.provider.subsonic

import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.provider.subsonic.http.SubsonicService
import dev.zacsweers.metro.Inject

/**
 * [SubsonicRemoteArtworkProvider]'s urls, signed, for a platform whose image loader can't sign a request on its way out
 * (iOS). Subsonic takes credentials only as query parameters, never a header ([RemoteArtworkProvider.requestHeaders]),
 * so each signed url carries a fresh salt: the artwork urls stay unsigned, keying the image cache, and [requestUrl] signs
 * one as it's requested.
 */
@Inject
class SignedSubsonicArtworkProvider(
    private val artwork: SubsonicRemoteArtworkProvider,
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : RemoteArtworkProvider by artwork {
    /** [url] signed with the stored credentials, when it's the signed-in server's; null when signed out. */
    override fun requestUrl(url: String): String? {
        if (!isSubsonicArtworkRequest(url, authenticationManager.getAddress())) return url
        val credentials = authenticationManager.getAuthenticatedCredentials() ?: return null
        return service.sign(url, authenticationManager.auth(credentials))
    }
}
