package com.simplecityapps.provider.plex

import com.simplecityapps.mediaprovider.server.ServerCredentialStore
import com.simplecityapps.provider.plex.http.PLEX_TOKEN
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Plex token to the image loader's artwork requests bound for the signed-in Plex server ([plexArtworkToken]), so
 * [PlexRemoteArtworkProvider]'s urls never carry it. Contributed to the image loader's client only, never the API client. Reads the
 * credentials per request, so a new sign-in or server address applies straight away. As a network interceptor it sees each hop of a
 * redirect, and a request bound anywhere else has the token header and query parameter removed: OkHttp carries custom headers over
 * a redirect to another host, so one the server sent us to would otherwise get the token.
 */
class PlexArtworkTokenInterceptor(private val credentialStore: ServerCredentialStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = plexArtworkToken(request.url.toString(), credentialStore.address, credentialStore.authenticatedCredentials?.accessToken)
        val builder = request.newBuilder()
        if (token != null) {
            builder.header(PLEX_TOKEN, token)
        } else {
            builder.removeHeader(PLEX_TOKEN)
            if (request.url.queryParameter(PLEX_TOKEN) != null) builder.url(request.url.newBuilder().removeAllQueryParameters(PLEX_TOKEN).build())
        }
        return chain.proceed(builder.build())
    }
}
