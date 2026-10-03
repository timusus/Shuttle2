package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.mediaprovider.server.ServerLogin
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class SignInToServerTest {
    private val plex = FakeServerAuthentication()
    private val jellyfin = FakeServerAuthentication()
    private val analytics = RecordingAnalytics()
    private val signIn = SignInToServer(
        mapOf(MediaProviderType.Plex to plex, MediaProviderType.Jellyfin to jellyfin),
        MonetisationAnalytics(analytics),
        SignInFailureClassifier { SignInFailureReason.Unreachable },
    )
    private val login = ServerLogin("http://server:8096", "sam", "secret")

    @Test
    fun `a successful sign-in is recorded and remembers the login when asked`() = runTest {
        signIn(MediaProviderType.Jellyfin, login, rememberLogin = true) shouldBe SignInToServer.Result.Success

        jellyfin.authenticated shouldBe listOf(login)
        jellyfin.remembered shouldBe login
        plex.authenticated shouldBe emptyList()
        analytics.events shouldBe listOf(
            RecordingAnalytics.Event("server_connected", mapOf("type" to "jellyfin", "method" to "password")),
        )
    }

    @Test
    fun `a successful sign-in leaves the login unsaved when not asked to remember it`() = runTest {
        signIn(MediaProviderType.Plex, login, rememberLogin = false) shouldBe SignInToServer.Result.Success

        plex.remembered shouldBe null
        analytics.names shouldBe listOf("server_connected")
    }

    @Test
    fun `a failed sign-in reports why - and doesn't remember the login - and is recorded by its bucket alone`() = runTest {
        plex.failure = Exception("The server could not be reached.")

        signIn(MediaProviderType.Plex, login, rememberLogin = true) shouldBe SignInToServer.Result.Failure("The server could not be reached.")

        plex.remembered shouldBe null
        analytics.events shouldBe listOf(
            RecordingAnalytics.Event("sign_in_failed", mapOf("type" to "plex", "method" to "password", "reason" to "unreachable")),
        )
    }

    @Test
    fun `a failure without a message reports an unknown error`() = runTest {
        plex.failure = IllegalStateException()

        signIn(MediaProviderType.Plex, login, rememberLogin = true) shouldBe SignInToServer.Result.Failure("An unknown error occurred.")
    }
}
