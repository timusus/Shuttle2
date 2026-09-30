package com.simplecityapps.shuttle.playback

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Keeps each play context's resume point (#670) up to date, so a Jump back in tile can carry on where its queue was left:
 * the current song of the queue's [QueueOperations.playContext], how far into it, where it is in the queue and whether
 * shuffle was on. A queue with no context has none.
 *
 * A point is saved as each song becomes current, on a pause, a seek or any other jump in progress, and every
 * [SAVE_INTERVAL] while it plays; the last song of the queue playing through with repeat off marks it finished, so the
 * tile starts over next time, until that song plays again. Writes go to [ioDispatcher] one at a time, the latest point
 * winning over any not yet written.
 *
 * Started once at app start next to [RecordPlays], it runs for the life of the app on main.
 */
class RecordResumePoints(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playHistoryRepository: PlayHistoryRepository,
    private val appCoroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant
) {
    @Inject
    constructor(
        playbackOperations: PlaybackOperations,
        queueOperations: QueueOperations,
        playHistoryRepository: PlayHistoryRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(playbackOperations, queueOperations, playHistoryRepository, appCoroutineScope, ioDispatcher, { Clock.System.now() })

    private class Current(
        val uid: Long,
        val song: Song
    ) {
        var positionMs: Int? = null
        var finished = false
    }

    /** The current queue item; main thread only, as is everything below. */
    private var current: Current? = null

    private var lastSavedAt: Instant? = null

    /** The next point to write; conflated, so a slow write drops the ones it overtakes. */
    private val pending = MutableStateFlow<ResumePoint?>(null)

    fun start() {
        appCoroutineScope.launch(ioDispatcher) {
            pending.filterNotNull().collect { playHistoryRepository.saveResumePoint(it) }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            queueOperations.queueStateFlow.distinctUntilChangedBy { it.currentItem?.uid }.collect { state ->
                val item = state.currentItem
                current = item?.let { Current(it.uid, it.song) }
                current?.let { save(it) }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.progressFlow.collect { progress ->
                val point = current ?: return@collect
                val position = progress?.position ?: return@collect
                val previous = point.positionMs
                point.positionMs = position
                val playing = playbackOperations.playbackStateFlow.value == PlaybackState.Playing
                if (playing && previous != null && position < previous) point.finished = false
                val tick = playing && previous != null && position - previous in 1..RecordPlays.MAX_TICK_MS
                val due = lastSavedAt?.let { now() - it >= SAVE_INTERVAL } ?: true
                if (!tick || due) save(point)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.pausePositionFlow.collect { songPosition ->
                val point = current?.takeIf { it.song.id == songPosition.song.id } ?: return@collect
                point.positionMs = songPosition.positionMs
                save(point)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { song ->
                val point = current?.takeIf { it.song.id == song.id } ?: return@collect
                val state = queueOperations.queueStateFlow.value
                if (state.currentPosition == state.items.lastIndex && queueOperations.getRepeatMode() == RepeatMode.Off) {
                    point.finished = true
                    save(point)
                }
            }
        }
    }

    private fun save(point: Current) {
        // The queue's context as it is now: a restored queue gets its context back after its current item.
        val context = queueOperations.playContext.takeIf { it != PlayContext.None } ?: return
        val state = queueOperations.queueStateFlow.value
        val track = state.currentPosition ?: return
        val savedAt = now()
        lastSavedAt = savedAt
        pending.value = ResumePoint(
            context = context,
            mediaProvider = point.song.mediaProvider,
            songPath = point.song.path,
            positionMs = (point.positionMs ?: 0).toLong(),
            track = track,
            trackCount = state.items.size,
            shuffled = state.shuffleMode == ShuffleMode.On,
            finished = point.finished,
            updatedAt = savedAt
        )
    }

    companion object {
        /** How often a point is saved while its song plays on. */
        val SAVE_INTERVAL = 10.seconds
    }
}
