package com.simplecityapps.shuttle.entitlement

import kotlin.time.Duration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What [ServerAccessGate.streamFromServer] decided. */
enum class ServerAccess {
    Allowed,
    Refused,

    /** The store hadn't answered ([Entitlement.Unknown]), so a purchaser couldn't be told apart from a new user. */
    Undecided
}

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
 * A refusal also asks for the paywall through [paywallRequests], so each entry point makes one call. While the store
 * hasn't answered, a stream or download waits up to [storeAnswerWait] for it; if it still hasn't, the action is
 * refused as [ServerAccess.Undecided], without the paywall, which a purchaser mustn't be shown.
 *
 * @param startTrial starts the server trial if the user hasn't had one (Android's
 *   `EntitlementRepository.startServerTrialIfEligible`), or null where only the paywall can start it (iOS).
 * @param storeAnswerWait how long a stream or download waits for the store's first answer: none on Android, whose
 *   cached Pro answers at once; a few seconds on iOS, where StoreKit answers soon after launch.
 */
class ServerAccessGate(
    private val entitlement: StateFlow<Entitlement>,
    private val startTrial: (suspend () -> Boolean)?,
    private val storeAnswerWait: Duration = Duration.ZERO
) {
    private val _paywallRequests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Where a refused action wants the paywall opened from. Dropped while nothing on screen collects it. */
    val paywallRequests: SharedFlow<PaywallSource> = _paywallRequests.asSharedFlow()

    /** True if the user may add a remote server: anyone but a user whose trial has ended without Pro. */
    fun tryAddServer(): Boolean {
        val allowed = (entitlement.value as? Entitlement.Free)?.trialUsed != true
        if (!allowed) _paywallRequests.tryEmit(PaywallSource.AddServer)
        return allowed
    }

    /**
     * True if the user may stream a song from a remote server, starting the trial for a user who hasn't had one where
     * it starts without asking. A downloaded song plays without asking.
     */
    suspend fun tryStreamFromServer(): Boolean = streamFromServer() == ServerAccess.Allowed

    /**
     * Whether the user may stream a song from a remote server, as [tryStreamFromServer], and why not. A refusal asks
     * for the paywall only if [askForPaywall]: a load the user didn't ask for (a queue restored at launch) mustn't open it.
     */
    suspend fun streamFromServer(askForPaywall: Boolean = true): ServerAccess = access(PaywallSource.ServerPlayback, askForPaywall)

    /**
     * True if the user may start a new download from a remote server, starting the trial for a user who hasn't had
     * one where it starts without asking. Existing downloads are never removed.
     */
    suspend fun tryDownloadFromServer(): Boolean = access(PaywallSource.ServerDownload, askForPaywall = true) == ServerAccess.Allowed

    private suspend fun access(
        source: PaywallSource,
        askForPaywall: Boolean
    ): ServerAccess {
        val access = unlocksServers(storeAnswer())
        if (access == ServerAccess.Refused && askForPaywall) _paywallRequests.tryEmit(source)
        return access
    }

    /** The entitlement once the store has answered, waiting up to [storeAnswerWait] for it; else [Entitlement.Unknown]. */
    private suspend fun storeAnswer(): Entitlement {
        val now = entitlement.value
        if (now !is Entitlement.Unknown || !storeAnswerWait.isPositive()) return now
        return withTimeoutOrNull(storeAnswerWait) { entitlement.first { it !is Entitlement.Unknown } } ?: entitlement.value
    }

    /**
     * Pro and a running trial unlock servers, and so does a trial not yet had, which this starts where it can. While
     * the store hasn't answered ([Entitlement.Unknown]) a purchaser can't be told apart from a new user, so nothing is
     * unlocked and the trial isn't started.
     */
    private suspend fun unlocksServers(entitlement: Entitlement): ServerAccess = when (entitlement) {
        is Entitlement.Pro, is Entitlement.Trial -> ServerAccess.Allowed

        is Entitlement.Free -> if (entitlement.trialUsed) {
            ServerAccess.Refused
        } else {
            val startTrial = startTrial
            if (startTrial == null) {
                // Only the paywall can start it: the refusal opens it, offering the trial.
                ServerAccess.Refused
            } else {
                // False only if another caller started it first, or the store reports Pro: either way servers are unlocked.
                startTrial()
                ServerAccess.Allowed
            }
        }

        Entitlement.Unknown -> ServerAccess.Undecided
    }
}
