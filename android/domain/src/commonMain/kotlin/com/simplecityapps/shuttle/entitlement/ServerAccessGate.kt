package com.simplecityapps.shuttle.entitlement

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What feature code asks before it uses a remote server. Only adding a server, streaming from one and new
 * downloads from one are gated. Local playback, Chromecast, AirPlay, EQ, Android Auto and songs that are already
 * downloaded are never gated, so they must not call this.
 *
 * The trial starts on the first stream or download from a server, so the 14 days count from when the user first gets
 * something from a server rather than from signing in, and a cancelled sign-in doesn't use them up. Where the trial
 * starts without asking (Android), that first stream or download starts it; where starting it needs the user's consent
 * (iOS, where the trial is a free App Store purchase), it's refused and the paywall offers the trial instead.
 *
 * A refusal also asks for the paywall through [paywallRequests], so each entry point makes one call.
 *
 * @param startTrial starts the server trial if the user hasn't had one (Android's
 *   `EntitlementRepository.startServerTrialIfEligible`), or null where only the paywall can start it (iOS).
 */
class ServerAccessGate(
    private val entitlement: StateFlow<Entitlement>,
    private val startTrial: (suspend () -> Boolean)?
) {
    private val _paywallRequests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Where a refused action wants the paywall opened from. Dropped while nothing on screen collects it. */
    val paywallRequests: SharedFlow<PaywallSource> = _paywallRequests.asSharedFlow()

    /** True if the user may add a remote server: anyone but a user whose trial has ended without Pro. */
    fun tryAddServer(): Boolean = allowOrAskForPaywall(
        allowed = (entitlement.value as? Entitlement.Free)?.trialUsed != true,
        source = PaywallSource.AddServer
    )

    /**
     * True if the user may stream a song from a remote server, starting the trial for a user who hasn't had one where
     * it starts without asking. A downloaded song plays without asking.
     */
    suspend fun tryStreamFromServer(): Boolean = allowOrAskForPaywall(unlocksServers(), PaywallSource.ServerPlayback)

    /**
     * True if the user may start a new download from a remote server, starting the trial for a user who hasn't had
     * one where it starts without asking. Existing downloads are never removed.
     */
    suspend fun tryDownloadFromServer(): Boolean = allowOrAskForPaywall(unlocksServers(), PaywallSource.ServerDownload)

    private fun allowOrAskForPaywall(
        allowed: Boolean,
        source: PaywallSource
    ): Boolean {
        if (!allowed) _paywallRequests.tryEmit(source)
        return allowed
    }

    /**
     * Pro and a running trial unlock servers, and so does a trial not yet had, which this starts where it can. While
     * the store hasn't answered ([Entitlement.Unknown]) a purchaser can't be told apart from a new user, so nothing is
     * unlocked and the trial isn't started.
     */
    private suspend fun unlocksServers(): Boolean = when (val entitlement = entitlement.value) {
        is Entitlement.Pro, is Entitlement.Trial -> true

        is Entitlement.Free -> if (entitlement.trialUsed) {
            false
        } else {
            val startTrial = startTrial
            if (startTrial == null) {
                // Only the paywall can start it: the refusal opens it, offering the trial.
                false
            } else {
                // False only if another caller started it first, or the store reports Pro: either way servers are unlocked.
                startTrial()
                true
            }
        }

        Entitlement.Unknown -> false
    }
}
