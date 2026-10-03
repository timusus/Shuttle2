package com.simplecityapps.shuttle.shared.entitlement

import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Server streaming on iOS needs Pro or the trial, as Android's `EntitledServerStreamPolicy` decides it; the stream
 * resolver asks [access] before it builds a server song's URL. iOS has no offline downloads yet, so there's no
 * downloaded copy to play past the gate. One instance for the graph, so [gatedSongs] sees every refusal.
 */
class GatedServerStreams(
    private val gate: ServerAccessGate
) {
    private val _gatedSongs = MutableSharedFlow<Song>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A queued song, each time it's skipped because streaming it needs Shuttle Music Pro. */
    val gatedSongs: SharedFlow<Song> = _gatedSongs.asSharedFlow()

    /**
     * Whether [song] may stream, waiting a little for StoreKit's answer at launch. Only a play the user asked for
     * ([playRequested]) reports a refusal, opening the paywall and [gatedSongs]: a queue restored at launch doesn't.
     */
    suspend fun access(
        song: Song,
        playRequested: Boolean
    ): ServerAccess {
        val access = gate.streamFromServer(askForPaywall = playRequested)
        if (access == ServerAccess.Refused && playRequested) _gatedSongs.tryEmit(song)
        return access
    }
}
