package com.simplecityapps.mediaprovider.server

import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/**
 * Sorts a Jellyfin, Emby or Plex sign-in failure by the network error under it: each provider wraps it in an exception
 * carrying the user's message (`userDescription()`), so this walks the cause chain. TLS and timeouts are told apart by
 * the exception's class name and message (OkHttp's `SSLHandshakeException`, Ktor's request timeout, Darwin's
 * `NSURLErrorDomain` codes); those are only read here, never sent.
 */
@ContributesBinding(AppScope::class)
@Inject
class NetworkSignInFailureClassifier : SignInFailureClassifier {
    override fun classify(error: Throwable): SignInFailureReason {
        val chain = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(MAX_CAUSES).toList()
        chain.firstNotNullOfOrNull { it as? RemoteServiceHttpError }?.let { http ->
            return when (http.httpStatusCode.value) {
                401, 403 -> SignInFailureReason.Auth
                in 500..599 -> SignInFailureReason.ServerError
                else -> SignInFailureReason.Other
            }
        }
        val network = chain.firstNotNullOfOrNull { it as? NetworkError }
        if (network != null && !network.hasInternetConnectivity) return SignInFailureReason.Offline
        val underlying = chain + listOfNotNull(network?.t)
        return when {
            underlying.any { it.looksLikeTls() } -> SignInFailureReason.Tls
            underlying.any { it.looksLikeTimeout() } -> SignInFailureReason.Timeout
            network != null -> SignInFailureReason.Unreachable
            else -> SignInFailureReason.Other
        }
    }

    private fun Throwable.looksLikeTls(): Boolean {
        val name = this::class.simpleName.orEmpty()
        val text = message.orEmpty()
        return name.startsWith("SSL") ||
            "certificate" in text.lowercase() ||
            DARWIN_TLS_CODES.any { "Code=$it" in text }
    }

    private fun Throwable.looksLikeTimeout(): Boolean {
        val name = this::class.simpleName.orEmpty()
        val text = message.orEmpty()
        return "Timeout" in name || "timed out" in text.lowercase() || "Code=-1001" in text
    }

    private companion object {
        const val MAX_CAUSES = 10

        // NSURLErrorSecureConnectionFailed through NSURLErrorClientCertificateRequired, and the App Transport Security
        // refusal (-1022)
        val DARWIN_TLS_CODES = listOf("-1200", "-1201", "-1202", "-1203", "-1204", "-1205", "-1206", "-1022")
    }
}
