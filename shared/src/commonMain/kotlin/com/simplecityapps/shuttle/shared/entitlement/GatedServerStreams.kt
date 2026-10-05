package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.model.Song

/**
 * Server streaming on iOS needs Pro or the trial, as Android's `EntitledServerStreamPolicy` decides it; the stream
 * resolver asks [access] before it builds a server song's stream URL. A song with a completed download plays from its
 * file without asking, so a lapsed trial keeps what was downloaded.
 */
class GatedServerStreams(
    private val gate: ServerAccessGate
) {
    /**
     * Whether [song] may stream, waiting a little for StoreKit's answer at launch. Only a play the user asked for
     * ([playRequested]) reports a refusal, opening the paywall: a queue restored at launch, or a next song resolved
     * ahead of time, doesn't. The song stays current, paused, rather than being skipped.
     */
    suspend fun access(
        song: Song,
        playRequested: Boolean
    ): ServerAccess = gate.streamFromServer(askForPaywall = playRequested)
}
