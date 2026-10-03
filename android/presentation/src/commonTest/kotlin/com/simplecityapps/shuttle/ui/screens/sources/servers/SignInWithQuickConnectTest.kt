package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakeQuickConnectAuthentication
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.mediaprovider.server.QuickConnectPollState
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SignInWithQuickConnectTest {
    private val quickConnect = FakeQuickConnectAuthentication()
    private val analytics = RecordingAnalytics()
    private val signIn = SignInWithQuickConnect(
        mapOf(MediaProviderType.Jellyfin to quickConnect),
        MonetisationAnalytics(analytics),
        SignInFailureClassifier { SignInFailureReason.Offline },
    )

    private fun signInFailed(reason: String) = RecordingAnalytics.Event("sign_in_failed", mapOf("type" to "jellyfin", "method" to "quick_connect", "reason" to reason))

    @Test
    fun `approval authenticates and reports success`() = runTest {
        quickConnect.pollState = QuickConnectPollState.Authenticated

        val states = signIn(MediaProviderType.Jellyfin, "http://server").toList()

        states shouldBe listOf(SignInWithQuickConnect.State.AwaitingApproval("123456"), SignInWithQuickConnect.State.Success)
        quickConnect.authenticated shouldBe listOf("http://server" to "secret-1")
        analytics.events shouldBe listOf(
            RecordingAnalytics.Event("server_connected", mapOf("type" to "jellyfin", "method" to "quick_connect")),
        )
    }

    @Test
    fun `denial on poll fails with why`() = runTest {
        quickConnect.pollState = QuickConnectPollState.Denied

        val states = signIn(MediaProviderType.Jellyfin, "http://server").toList()

        states shouldBe listOf(
            SignInWithQuickConnect.State.AwaitingApproval("123456"),
            SignInWithQuickConnect.State.Failed("Quick Connect sign-in was denied."),
        )
        quickConnect.authenticated shouldBe emptyList()
        analytics.events shouldBe listOf(signInFailed("denied"))
    }

    @Test
    fun `no approval within the window expires`() = runTest {
        quickConnect.pollState = QuickConnectPollState.Pending

        val states = signIn(MediaProviderType.Jellyfin, "http://server").toList()

        states shouldBe listOf(SignInWithQuickConnect.State.AwaitingApproval("123456"), SignInWithQuickConnect.State.Expired)
        quickConnect.authenticated shouldBe emptyList()
        analytics.events shouldBe listOf(signInFailed("expired"))
    }

    @Test
    fun `a failure initiating is reported without polling`() = runTest {
        quickConnect.initiateFailure = IllegalStateException("boom")

        val states = signIn(MediaProviderType.Jellyfin, "http://server").toList()

        states shouldBe listOf(SignInWithQuickConnect.State.Failed("boom"))
        analytics.events shouldBe listOf(signInFailed("offline"))
    }

    @Test
    fun `cancelling stops polling and never authenticates`() = runTest {
        quickConnect.pending = CompletableDeferred()
        val states = mutableListOf<SignInWithQuickConnect.State>()

        val job = launch { signIn(MediaProviderType.Jellyfin, "http://server").collect { states += it } }
        runCurrent()
        job.cancel()
        job.join()

        states shouldBe listOf(SignInWithQuickConnect.State.AwaitingApproval("123456"))
        quickConnect.authenticated shouldBe emptyList()
        analytics.events shouldBe emptyList()
    }
}
