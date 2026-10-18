package com.simplecityapps.shuttle.playback

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Saves a context's resume point only once its song plays, so a restored queue doesn't overwrite it. */
class RecordResumePoints(
    private val playbackStateSource: PlaybackStateSource,
    private val queueOperations: QueueOperations,
    private val playHistoryRepository: PlayHistoryRepository,
    private val appCoroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant
) {
    @Inject
    constructor(
        playbackStateSource: PlaybackStateSource,
        queueOperations: QueueOperations,
        playHistoryRepository: PlayHistoryRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(playbackStateSource, queueOperations, playHistoryRepository, appCoroutineScope, ioDispatcher, { Clock.System.now() })

    private class Current(
        val uid: Long,
        val song: Song
    ) {
        var positionMs: Int? = null
        var finished = false

        /** The queue's context as the song last played; null until it plays, and nothing is saved before then. */
        var context: PlayContext? = null

        var track: Int? = null
        var trackCount = 0
        var shuffled = false
    }

    /** The current queue item; main thread only, as is everything below. */
    private var current: Current? = null

    private var lastSavedAt: Instant? = null

    /** The latest point held back by [WRITE_INTERVAL], and the job that writes it once that's up. */
    private var heldBack: ResumePoint? = null
    private var heldBackWrite: Job? = null

    private val writes = Channel<ResumePoint>(Channel.UNLIMITED)

    fun start() {
        appCoroutineScope.launch(ioDispatcher) {
            for (point in writes) {
                try {
                    playHistoryRepository.saveResumePoint(point)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error(e) { "Failed to save the resume point of ${point.context}" }
                }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            queueOperations.queueStateFlow.collect { state ->
                val item = state.currentItem
                if (item?.uid != current?.uid) {
                    current?.let { save(it) }
                    current = item?.let { Current(it.uid, it.song) }
                }
                current?.apply {
                    track = state.currentPosition
                    trackCount = state.items.size
                    shuffled = state.shuffleMode == ShuffleMode.On
                }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.progressFlow.collect { progress ->
                val point = current ?: return@collect
                val position = progress?.position ?: return@collect
                val previous = point.positionMs
                point.positionMs = position
                val playing = playbackStateSource.playbackStateFlow.value == PlaybackState.Playing
                if (playing && previous != null && position < previous) point.finished = false
                val tick = playing && previous != null && position - previous in 1..RecordPlays.MAX_TICK_MS
                // A restored queue gets its context back after its current item, and a song only settles by playing on.
                val settles = tick && point.context == null
                if (tick) point.context = queueOperations.playContext
                val due = lastSavedAt?.let { now() - it >= SAVE_INTERVAL } ?: true
                if (!tick || settles || due) save(point)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.pausePositionFlow.collect { songPosition ->
                val point = current?.takeIf { it.song.id == songPosition.song.id } ?: return@collect
                point.positionMs = songPosition.positionMs
                save(point, promptly = true)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.trackEndedFlow.collect { end ->
                val point = current?.takeIf { it.song.id == end.song.id } ?: return@collect
                val state = queueOperations.queueStateFlow.value
                if (state.currentPosition == state.items.lastIndex && queueOperations.getRepeatMode() == RepeatMode.Off) {
                    point.finished = true
                    save(point, promptly = true)
                }
            }
        }
    }

    private fun save(
        point: Current,
        promptly: Boolean = false
    ) {
        val context = point.context?.takeIf { it != PlayContext.None } ?: return
        val track = point.track ?: return
        val resumePoint = ResumePoint(
            context = context,
            mediaProvider = point.song.mediaProvider,
            songPath = point.song.path,
            positionMs = (point.positionMs ?: 0).toLong(),
            track = track,
            trackCount = point.trackCount,
            shuffled = point.shuffled,
            finished = point.finished,
            updatedAt = now()
        )
        // Another context's point held back goes first, so each context keeps its own.
        heldBack?.takeIf { it.context != context }?.let(::write)
        val due = lastSavedAt?.let { resumePoint.updatedAt - it >= WRITE_INTERVAL } ?: true
        if (promptly || due) {
            write(resumePoint)
        } else {
            heldBack = resumePoint
            if (heldBackWrite == null) {
                heldBackWrite = appCoroutineScope.launch(Dispatchers.Main.immediate) {
                    delay(WRITE_INTERVAL)
                    heldBackWrite = null
                    heldBack?.let(::write)
                }
            }
        }
    }

    private fun write(point: ResumePoint) {
        if (heldBack?.context == point.context) {
            heldBack = null
            heldBackWrite?.cancel()
            heldBackWrite = null
        }
        lastSavedAt = point.updatedAt
        writes.trySend(point)
    }

    companion object {
        /** How often a point is saved while its song plays on. */
        val SAVE_INTERVAL = 10.seconds

        /** The least time between two writes, bar the ones that go straight away. */
        val WRITE_INTERVAL = 2.seconds

        private val logger = Logger.tagged("RecordResumePoints")
    }
}
