package com.simplecityapps.shuttle.ui.screens.sources.servers

import com.simplecityapps.fakes.FakePinAuthentication
import com.simplecityapps.fakes.RecordingAnalytics
import com.simplecityapps.shuttle.analytics.MonetisationAnalytics
import com.simplecityapps.shuttle.analytics.SignInFailureClassifier
import com.simplecityapps.shuttle.analytics.SignInFailureReason
import com.simplecityapps.shuttle.model.MediaProviderType
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SignInWithPinTest {
    private val plex = FakePinAuthentication()
    private val analytics = RecordingAnalytics()
    private val signIn = SignInWithPin(
        mapOf(MediaProviderType.Plex to plex),
        MonetisationAnalytics(analytics),
        SignInFailureClassifier { SignInFailureReason.Offline },
    )

    private fun signInFailed(reason: String) = RecordingAnalytics.Event("sign_in_failed", mapOf("type" to "plex", "method" to "pin", "reason" to reason))

    @Test
    fun `approval lists the account's servers - the user's own first - with the account's token`() = runTest {
        plex.approveAfterChecks = 2
        val shared = FakePinAuthentication.server("shared", "Alpha", owned = false)
        val own = FakePinAuthentication.server("home", "Zulu")
        plex.servers = listOf(shared, own)

        val states = signIn(MediaProviderType.Plex).toList()

        states shouldBe listOf(SignInWithPin.State.AwaitingApproval(plex.pin), SignInWithPin.State.Approved(listOf(own, shared)))
        plex.checkCount shouldBe 3
        plex.serversRequestedWith shouldBe listOf(FakePinAuthentication.ACCOUNT_TOKEN)
        analytics.events shouldBe emptyList()
    }

    @Test
    fun `a PIN nobody approves expires`() = runTest {
        plex.approveAfterChecks = null
        plex.pin = plex.pin.copy(expiresInSeconds = 10)

        val states = signIn(MediaProviderType.Plex).toList()

        states shouldBe listOf(SignInWithPin.State.AwaitingApproval(plex.pin), SignInWithPin.State.Expired)
        analytics.events shouldBe listOf(signInFailed("expired"))
    }

    @Test
    fun `a PIN that can't be created fails with its message`() = runTest {
        plex.createFailure = IllegalStateException("Couldn't reach plex.tv.")

        signIn(MediaProviderType.Plex).toList() shouldBe listOf(SignInWithPin.State.Failed("Couldn't reach plex.tv."))
        analytics.events shouldBe listOf(signInFailed("offline"))
    }

    @Test
    fun `a failed check stops the polling`() = runTest {
        plex.checkFailure = IllegalStateException("Couldn't reach plex.tv.")

        val states = signIn(MediaProviderType.Plex).toList()

        states shouldBe listOf(SignInWithPin.State.AwaitingApproval(plex.pin), SignInWithPin.State.Failed("Couldn't reach plex.tv."))
        plex.checkCount shouldBe 0
    }

    @Test
    fun `an account with no servers fails`() = runTest {
        plex.servers = emptyList()

        val states = signIn(MediaProviderType.Plex).toList()

        states.last() shouldBe SignInWithPin.State.Failed("There's no Plex Media Server on this account.")
        analytics.events shouldBe listOf(signInFailed("other"))
    }

    @Test
    fun `backing out stops the polling and records nothing`() = runTest {
        plex.approveAfterChecks = null
        val job = launch { signIn(MediaProviderType.Plex).collect {} }
        advanceTimeBy(6_001)
        runCurrent()
        val checks = plex.checkCount

        job.cancel()
        advanceTimeBy(60_000)

        plex.checkCount shouldBe checks
        analytics.events shouldBe emptyList()
    }
}
