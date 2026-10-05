package com.simplecityapps.provider.subsonic

import com.simplecityapps.provider.subsonic.http.SubsonicService
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Signs the image loader's `getCoverArt` requests bound for the signed-in Subsonic server ([isSubsonicArtworkRequest]),
 * so [SubsonicRemoteArtworkProvider]'s urls never carry credentials. Contributed to the image loader's client only,
 * never the API client. Reads the credentials per request, so a new sign-in or server address applies straight away. A
 * request bound anywhere else (a redirect to another host) has any credential parameters removed.
 */
class SubsonicArtworkAuthInterceptor(
    private val authenticationManager: SubsonicAuthenticationManager,
    private val service: SubsonicService
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val credentials = authenticationManager.getAuthenticatedCredentials()
        if (credentials != null && isSubsonicArtworkRequest(url.toString(), authenticationManager.getAddress())) {
            return chain.proceed(request.newBuilder().url(service.sign(url.toString(), authenticationManager.auth(credentials))).build())
        }
        if (SUBSONIC_CREDENTIAL_PARAMETERS.none { url.queryParameter(it) != null }) return chain.proceed(request)
        val stripped = url.newBuilder().apply { SUBSONIC_CREDENTIAL_PARAMETERS.forEach(::removeAllQueryParameters) }.build()
        return chain.proceed(request.newBuilder().url(stripped).build())
    }
}
