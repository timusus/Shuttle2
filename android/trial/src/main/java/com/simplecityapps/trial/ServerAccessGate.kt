package com.simplecityapps.trial

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * What feature code asks before it uses a remote server. Only adding a server, streaming from one and new
 * downloads from one are gated. Local playback, Chromecast, EQ, Android Auto and songs that are already
 * downloaded are never gated, so they must not call this.
 *
 * A refusal also asks for the paywall through [paywallRequests], so each entry point makes one call.
 */
class ServerAccessGate(
    private val entitlement: StateFlow<Entitlement>
) {
    private val _paywallRequests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Where a refused action wants the paywall opened from. Dropped while nothing on screen collects it. */
    val paywallRequests: SharedFlow<PaywallSource> = _paywallRequests.asSharedFlow()

    /** True if the user may add a remote server: anyone but a user whose trial has ended without Pro. */
    fun tryAddServer(): Boolean = allowOrAskForPaywall(
        allowed = (entitlement.value as? Entitlement.Free)?.trialUsed != true,
        source = PaywallSource.AddServer
    )

    /** True if the user may stream a song from a remote server. A downloaded song plays without asking. */
    fun tryStreamFromServer(): Boolean = allowOrAskForPaywall(entitlement.value.unlocksServers(), PaywallSource.ServerPlayback)

    /** True if the user may start a new download from a remote server. Existing downloads are never removed. */
    fun tryDownloadFromServer(): Boolean = allowOrAskForPaywall(
        allowed = !SERVER_DOWNLOADS_NEED_PRO || entitlement.value.unlocksServers(),
        source = PaywallSource.ServerDownload
    )

    private fun allowOrAskForPaywall(
        allowed: Boolean,
        source: PaywallSource
    ): Boolean {
        if (!allowed) _paywallRequests.tryEmit(source)
        return allowed
    }

    private fun Entitlement.unlocksServers(): Boolean = this is Entitlement.Pro || this is Entitlement.Trial

    companion object {
        /** New downloads from a server need Pro, like streaming (docs/product/monetisation.md, pending decision). */
        const val SERVER_DOWNLOADS_NEED_PRO = true
    }
}
