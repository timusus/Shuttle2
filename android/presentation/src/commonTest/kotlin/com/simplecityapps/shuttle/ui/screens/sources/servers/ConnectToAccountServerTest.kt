package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakePinAuthentication
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ConnectToAccountServerTest {
    private val plex = FakePinAuthentication()
    private val analytics = RecordingAnalytics()
    private val connect = ConnectToAccountServer(
        mapOf(MediaProviderType.Plex to plex),
        MonetisationAnalytics(analytics),
        SignInFailureClassifier { SignInFailureReason.Unreachable },
    )
    private val home = FakePinAuthentication.server("home", "Home")

    @Test
    fun `connecting to another server switches to it and records the sign-in`() = runTest {
        plex.signedInTo = "old"

        connect(MediaProviderType.Plex, home) shouldBe ConnectToAccountServer.Result.Success(switchedServer = true)

        plex.connected shouldBe listOf(home)
        analytics.events shouldBe listOf(RecordingAnalytics.Event("server_connected", mapOf("type" to "plex", "method" to "pin")))
    }

    @Test
    fun `connecting to the server already signed in isn't a switch`() = runTest {
        plex.signedInTo = "home"

        connect(MediaProviderType.Plex, home) shouldBe ConnectToAccountServer.Result.Success(switchedServer = false)
    }

    @Test
    fun `a server that can't be reached fails with its message`() = runTest {
        plex.connectFailure = IllegalStateException("Couldn't reach Home.")

        connect(MediaProviderType.Plex, home) shouldBe ConnectToAccountServer.Result.Failure("Couldn't reach Home.")
        analytics.events shouldBe listOf(
            RecordingAnalytics.Event("sign_in_failed", mapOf("type" to "plex", "method" to "pin", "reason" to "unreachable")),
        )
    }
}
