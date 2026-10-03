package com.simplecityapps.mediaprovider.server

import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.retrofit.error.UnexpectedError
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.io.IOException

class NetworkSignInFailureClassifierTest {
    private val classifier = NetworkSignInFailureClassifier()

    /** As a provider's `ServerAuthentication` hands it over: the user's message, the network error as its cause. */
    private fun wrapped(error: Throwable) = Exception("The server could not be reached.", error)

    @Test
    fun `refused credentials are auth`() {
        classifier.classify(wrapped(RemoteServiceHttpError(HttpStatusCode.Unauthorized))) shouldBe SignInFailureReason.Auth
        classifier.classify(wrapped(RemoteServiceHttpError(HttpStatusCode.Forbidden))) shouldBe SignInFailureReason.Auth
    }

    @Test
    fun `a 5xx is a server error and any other status is other`() {
        classifier.classify(wrapped(RemoteServiceHttpError(HttpStatusCode.BadGateway))) shouldBe SignInFailureReason.ServerError
        classifier.classify(wrapped(RemoteServiceHttpError(HttpStatusCode.NotFound))) shouldBe SignInFailureReason.Other
    }

    @Test
    fun `no connection is offline`() {
        classifier.classify(wrapped(NetworkError(hasInternetConnectivity = false, IOException("x")))) shouldBe SignInFailureReason.Offline
    }

    @Test
    fun `a server that doesn't answer is unreachable`() {
        classifier.classify(wrapped(NetworkError(true, IOException("Connection refused")))) shouldBe SignInFailureReason.Unreachable
    }

    @Test
    fun `a certificate failure is tls`() {
        classifier.classify(wrapped(NetworkError(true, SSLHandshakeException("Trust anchor for certification path not found")))) shouldBe
            SignInFailureReason.Tls
        val darwin = IOException("Error Domain=NSURLErrorDomain Code=-1202 \"The certificate for this server is invalid.\"")
        classifier.classify(wrapped(NetworkError(true, darwin))) shouldBe SignInFailureReason.Tls
    }

    @Test
    fun `a timeout is timeout`() {
        classifier.classify(wrapped(NetworkError(true, RequestTimeoutException("Request timed out")))) shouldBe SignInFailureReason.Timeout
        classifier.classify(wrapped(NetworkError(true, IOException("Error Domain=NSURLErrorDomain Code=-1001")))) shouldBe
            SignInFailureReason.Timeout
    }

    @Test
    fun `anything else is other`() {
        classifier.classify(wrapped(UnexpectedError(IllegalStateException("bad json")))) shouldBe SignInFailureReason.Other
        classifier.classify(IllegalStateException()) shouldBe SignInFailureReason.Other
    }

    private class SSLHandshakeException(message: String) : IOException(message)

    private class RequestTimeoutException(message: String) : IOException(message)
}
