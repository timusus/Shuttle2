package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Plex token to the image loader's artwork requests bound for the signed-in Plex server ([plexArtworkToken]), so
 * [PlexRemoteArtworkProvider]'s urls never carry it. Contributed to the image loader's client only, never the API client. Reads the
 * credentials per request, so a new sign-in or server address applies straight away.
 */
class PlexArtworkTokenInterceptor(private val credentialStore: ServerCredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = plexArtworkToken(request.url.toString(), credentialStore.address, credentialStore.authenticatedCredentials?.accessToken)
            ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(PLEX_TOKEN, token).build())
    }
}
