package com.simplecityapps.provider.plex

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Plex token to artwork requests bound for the signed-in Plex server, so [PlexRemoteArtworkProvider]'s urls (and whatever caches or logs
 * them) never carry it. Reads the credentials per request, so a new sign-in or server address applies straight away; requests to any other
 * origin pass through untouched.
 */
class PlexArtworkTokenInterceptor(private val credentialStore: CredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val server = credentialStore.address?.toHttpUrlOrNull()
        val token = credentialStore.authenticatedCredentials?.accessToken
        if (server == null || token == null) return chain.proceed(request)

        val url = request.url
        val isPlexServer = url.scheme == server.scheme && url.host == server.host && url.port == server.port
        if (!isPlexServer) return chain.proceed(request)

        return chain.proceed(request.newBuilder().header(TOKEN_HEADER, token).build())
    }

    companion object {
        const val TOKEN_HEADER = "X-Plex-Token"
    }
}
