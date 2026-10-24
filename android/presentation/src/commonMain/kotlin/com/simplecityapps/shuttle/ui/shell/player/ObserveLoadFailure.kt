package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.SongLoadFailure
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Why the current song can't play, which the player says in place of its seek bar. */
sealed interface LoadFailure {
    /** The song's server couldn't be reached; the message opens Sources. */
    data class ServerUnreachable(val provider: MediaProviderType) : LoadFailure

    data object CantPlay : LoadFailure
}

/**
 * The [LoadFailure] of the current song while it stands: it clears when another load starts (a retry that works
 * becomes ready, one that doesn't fails again), when the current item changes and when the queue is replaced.
 */
class ObserveLoadFailure @Inject constructor(
    private val playbackStateSource: PlaybackStateSource,
    private val queueOperations: QueueOperations,
) {
    operator fun invoke(): Flow<LoadFailure?> = flow {
        var shown: Pair<Long, LoadFailure>? = null
        merge(
            playbackStateSource.loadFailureFlow.map { Event.Failed(it) },
            queueOperations.queueStateFlow.map { Event.Queue(it.currentItem?.uid) },
            playbackStateSource.playbackStateFlow.map { Event.State(it) },
        ).collect { event ->
            shown = when (event) {
                is Event.Failed -> {
                    val current = queueOperations.queueStateFlow.value.currentItem
                    if (current?.song?.id == event.failure.song.id) current.uid to event.failure.toLoadFailure() else shown
                }

                is Event.Queue -> shown?.takeIf { it.first == event.currentUid }

                is Event.State -> if (event.state == PlaybackState.Loading) null else shown
            }
            emit(shown?.second)
        }
    }.distinctUntilChanged()

    private fun SongLoadFailure.toLoadFailure(): LoadFailure = if (serverUnreachable && song.mediaProvider.remote) LoadFailure.ServerUnreachable(song.mediaProvider) else LoadFailure.CantPlay

    private sealed interface Event {
        data class Failed(val failure: SongLoadFailure) : Event

        data class Queue(val currentUid: Long?) : Event

        data class State(val state: PlaybackState) : Event
    }
}
