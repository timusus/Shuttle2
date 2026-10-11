package com.simplecityapps.shuttle.entitlement

import kotlin.time.Duration
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

/** What [ServerAccessGate] decided about a [ProFeature]. */
enum class ServerAccess {
    Allowed,
    Refused,

    /** The store hadn't answered ([Entitlement.Unknown]), so a purchaser couldn't be told apart from a new user. */
    Undecided
}

/**
 * What feature code asks before it uses a Shuttle Music Pro feature ([ProFeature]): a remote server (adding one,
 * streaming from one, new downloads from one), Android Auto, editing several songs' tags at once, and advanced audio.
 * Local playback, Chromecast, AirPlay, the graphic equalizer, single-song tag edits and songs that are already
 * downloaded are never gated, so they must not call this.
 *
 * There's one trial, shared by every feature, and the user starts it on their own (the paywall's and onboarding's
 * [StartTrial], and iOS, where the trial is a free App Store purchase): a use without Pro or a running trial is refused
 * and the paywall offers the trial. Refusing to add a server doesn't resume the add once the trial starts; the user
 * taps Add again. No production gate is built with [startTrial], which would start the trial on a feature's first use
 * and hold the feature in [pending] until the app has disclosed it.
 *
 * A refusal also asks for the paywall through [paywallRequests], so each entry point makes one call. While the store
 * hasn't answered, a use waits up to [storeAnswerWait] for it; if it still hasn't, the use is
 * [ServerAccess.Undecided], without the paywall, which a purchaser mustn't be shown. A stream or download from a server
 * is refused then; every other feature goes ahead ([tryUse]), so a purchaser is never locked out of one.
 *
 * @param startTrial starts the trial on a feature's first use if the user hasn't had one, or null where only the paywall
 *   can start it (Android and iOS in production).
 * @param storeAnswerWait how long a use waits for the store's first answer: none on Android, whose cached Pro answers
 *   at once; a few seconds on iOS, where StoreKit answers soon after launch.
 * @param offersTrialOnAddServer refuses adding a server to a user with neither Pro nor a used trial, so the paywall offers
 *   the trial first (Android; iOS lets a server be added until the trial is used).
 * @param disclosureStore keeps [pending] across process death, so a trial a car started is still disclosed after the
 *   app was killed; only used by a gate that starts the trial itself.
 */
class ServerAccessGate(
    private val entitlement: StateFlow<Entitlement>,
    private val startTrial: (suspend () -> Boolean)?,
    private val storeAnswerWait: Duration = Duration.ZERO,
    private val disclosureStore: TrialDisclosureStore = InMemoryTrialDisclosureStore(),
    private val offersTrialOnAddServer: Boolean = false
) : TrialDisclosures {
    private val _paywallRequests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Where a refused action wants the paywall opened from. Dropped while nothing on screen collects it. */
    val paywallRequests: SharedFlow<PaywallSource> = _paywallRequests.asSharedFlow()

    private val _pending = MutableStateFlow(disclosureStore.pendingDisclosure)

    override val pending: StateFlow<ProFeature?> = _pending.asStateFlow()

    override fun onDisclosed() {
        disclosureStore.pendingDisclosure = null
        _pending.value = null
    }

    /**
     * Whether Pro features are locked now (the trial has ended without Pro, or only the paywall can start it), rather
     * than unlocked or undecided. Changes when a purchase, the trial ending or the store's first answer changes that,
     * for a surface that can't ask again (a car's browse tree) to refresh.
     */
    val locked: Flow<Boolean> = entitlement
        .map(::locks)
        .distinctUntilChanged()

    /** Whether Pro features are locked right now, as [locked] next emits: its starting value for a surface that holds it. */
    val isLocked: Boolean get() = locks(entitlement.value)

    private fun locks(entitlement: Entitlement): Boolean = entitlement is Entitlement.Free && (entitlement.trialUsed || startTrial == null)

    /**
     * True if the user may add a remote server: anyone but a user whose trial has ended without Pro, and, where
     * [offersTrialOnAddServer], a user who hasn't had the trial either, so the paywall can offer it before the first server.
     */
    fun tryAddServer(): Boolean {
        val free = entitlement.value as? Entitlement.Free
        val allowed = free == null || (!free.trialUsed && !offersTrialOnAddServer)
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
    suspend fun streamFromServer(askForPaywall: Boolean = true): ServerAccess = use(ProFeature.Servers, PaywallSource.ServerPlayback, askForPaywall)

    /**
     * True if the user may start a new download from a remote server, starting the trial for a user who hasn't had
     * one where it starts without asking. Existing downloads are never removed.
     */
    suspend fun tryDownloadFromServer(): Boolean = use(ProFeature.Servers, PaywallSource.ServerDownload, askForPaywall = true) == ServerAccess.Allowed

    /**
     * Whether the user may use [feature] now, starting the trial for a user who hasn't had one where it starts without
     * asking. A refusal asks for the paywall only if [askForPaywall]: a car can't show it.
     */
    suspend fun use(
        feature: ProFeature,
        askForPaywall: Boolean = true
    ): ServerAccess = use(feature, feature.paywallSource, askForPaywall)

    /**
     * True unless [use] refuses [feature]: a store that hasn't answered yet ([ServerAccess.Undecided], a fresh install
     * or a Billing error) lets it through rather than block a purchaser. A refusal has asked for the paywall if
     * [askForPaywall].
     */
    suspend fun tryUse(
        feature: ProFeature,
        askForPaywall: Boolean = true
    ): Boolean = use(feature, askForPaywall) != ServerAccess.Refused

    private suspend fun use(
        feature: ProFeature,
        source: PaywallSource,
        askForPaywall: Boolean
    ): ServerAccess {
        val access = unlocks(feature, storeAnswer())
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
     * Pro and a running trial unlock every feature, and so does a trial not yet had, which this starts where it can.
     * While the store hasn't answered ([Entitlement.Unknown]) a purchaser can't be told apart from a new user, so
     * nothing is unlocked and the trial isn't started.
     */
    private suspend fun unlocks(
        feature: ProFeature,
        entitlement: Entitlement
    ): ServerAccess = when (entitlement) {
        is Entitlement.Pro, is Entitlement.Trial -> ServerAccess.Allowed

        is Entitlement.Free -> if (entitlement.trialUsed) {
            ServerAccess.Refused
        } else {
            val startTrial = startTrial
            if (startTrial == null) {
                // Only the paywall can start it: the refusal opens it, offering the trial.
                ServerAccess.Refused
            } else {
                // False only if another caller started it first, or the store reports Pro: either way the feature is unlocked.
                if (startTrial()) {
                    disclosureStore.pendingDisclosure = feature
                    _pending.value = feature
                }
                ServerAccess.Allowed
            }
        }

        Entitlement.Unknown -> ServerAccess.Undecided
    }
}

private class InMemoryTrialDisclosureStore : TrialDisclosureStore {
    override var pendingDisclosure: ProFeature? = null
}
