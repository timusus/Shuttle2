package com.simplecityapps.shuttle.playback

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Writes a listen once it reaches the threshold, so it survives the app being killed mid-song. */
class RecordPlays(
    private val playbackStateSource: PlaybackStateSource,
    private val queueOperations: QueueOperations,
    private val songRepository: SongRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val appCoroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant
) {
    @Inject
    constructor(
        playbackStateSource: PlaybackStateSource,
        queueOperations: QueueOperations,
        songRepository: SongRepository,
        playHistoryRepository: PlayHistoryRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(playbackStateSource, queueOperations, songRepository, playHistoryRepository, appCoroutineScope, ioDispatcher, { Clock.System.now() })

    private class Listen(
        val uid: Long,
        val song: Song,
        val startedAt: Instant,
        val context: PlayContext
    ) {
        var listenedMs: Long = 0
        var lastPositionMs: Int? = null

        /** Whether a progress tick was before the threshold: only then does reaching it count. */
        var wasBeforeThreshold = false

        /** The play's event id as it's written, once the listen reached the threshold. */
        var recorded: Deferred<Long?>? = null
    }

    /** The current item's listen; main thread only, as is everything below. */
    private var listen: Listen? = null

    /** The last listen, ended as the queue moved on, waiting [TRACK_END_GRACE_MS] for a track end. */
    private var ended: Pair<Listen, Job>? = null

    /** The flows are collected on main straight away, missing none of the events sent after. */
    fun start() {
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            queueOperations.queueStateFlow.distinctUntilChangedBy { it.currentItem?.uid }.collect { state ->
                val item = state.currentItem
                if (item?.uid != listen?.uid) {
                    endListen()
                    listen = item?.let { Listen(it.uid, it.song, now(), queueOperations.playContext) }
                }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.progressFlow.collect { progress ->
                val current = listen ?: return@collect
                val position = progress?.position ?: return@collect
                val playing = playbackStateSource.playbackStateFlow.value == PlaybackState.Playing
                val step = current.lastPositionMs?.let { position - it }
                if (step != null && step in 1..MAX_TICK_MS && playing) {
                    current.listenedMs += step
                }
                current.lastPositionMs = position
                val durationMs = progress.duration.takeIf { it > 0 } ?: current.song.duration
                val threshold = PlayHistoryRepository.listenThresholdMs(durationMs.toLong()) ?: return@collect
                when {
                    position < threshold -> current.wasBeforeThreshold = true
                    playing && current.wasBeforeThreshold && current.recorded == null -> record(current)
                }
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.trackEndedFlow.collect { end ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.recordPlayedThrough(end.song) }
                onTrackEnded(end)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackStateSource.pausePositionFlow.collect { songPosition ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.setPlaybackPosition(songPosition.song, songPosition.positionMs) }
            }
        }
    }

    private fun onTrackEnded(end: TrackEnd) {
        val pending = ended
        val current = listen
        when {
            pending != null && pending.first.uid == end.uid -> {
                pending.second.cancel()
                ended = null
                complete(pending.first)
            }

            current != null && current.uid == end.uid -> {
                complete(current)
                // It starts over on repeat, or the queue moves on and the fresh listen never reaches the threshold.
                listen = Listen(current.uid, current.song, now(), current.context)
            }
        }
    }

    /** Ends the current listen as the queue moves on: a track end for it may still follow, for a short while. */
    private fun endListen() {
        val current = listen ?: return
        ended?.second?.cancel()
        val job = appCoroutineScope.launch(Dispatchers.Main.immediate) {
            delay(TRACK_END_GRACE_MS)
            ended = null
        }
        ended = current to job
    }

    private fun record(listen: Listen) {
        val listenedMs = listen.listenedMs
        listen.recorded = appCoroutineScope.async(ioDispatcher) {
            playHistoryRepository.recordPlay(listen.song, listen.startedAt, listenedMs, completed = false, listen.context)
        }
    }

    /** Marks [listen]'s play completed, if it reached the threshold and was written. */
    private fun complete(listen: Listen) {
        val recorded = listen.recorded ?: return
        val listenedMs = listen.listenedMs
        appCoroutineScope.launch(ioDispatcher) {
            recorded.await()?.let { id -> playHistoryRepository.completePlay(id, listenedMs) }
        }
    }

    companion object {
        /** How long a listen waits for its track end after the queue moves on. */
        const val TRACK_END_GRACE_MS = 2_000L

        /** The largest step between progress ticks counted as listening (a tick at up to 2x speed, or a late one). */
        const val MAX_TICK_MS = 10_000
    }
}
