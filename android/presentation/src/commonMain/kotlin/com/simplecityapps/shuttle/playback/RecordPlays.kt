package com.simplecityapps.shuttle.playback

import com.simplecityapps.mediaprovider.repository.playhistory.PlayHistoryRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Records each song's plays, for Recently Played, Most Played, an artist's Top Songs and the listening history (#633),
 * on both platforms:
 * - a track end ([PlaybackOperations.trackEndedFlow]) counts the song as played through (its play count, last played
 *   and last completed), and writes a completed play event;
 * - moving off a song before its end, having listened to at least [PlayHistoryRepository.MIN_LISTENED_MS] of it,
 *   writes a play event that isn't completed;
 * - a pause ([PlaybackOperations.pausePositionFlow]) saves its position (and when it was last played).
 *
 * Each play of the current queue item is a listen: it starts when the item becomes current (or when it starts over on
 * repeat), with the queue's [QueueOperations.playContext] as it stands then. Its listened time adds up the progress
 * ticks while playing, counting only small steps forward, so a seek isn't listening and a skip back isn't negative.
 * A track end can reach this before or after the queue moves on, so a listen that ends without one waits
 * [TRACK_END_GRACE_MS] for it before it's written as not completed.
 *
 * Started once at app start, it runs for the life of the app on main; each write goes to [ioDispatcher].
 */
class RecordPlays(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val songRepository: SongRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val appCoroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Instant
) {
    @Inject
    constructor(
        playbackOperations: PlaybackOperations,
        queueOperations: QueueOperations,
        songRepository: SongRepository,
        playHistoryRepository: PlayHistoryRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(playbackOperations, queueOperations, songRepository, playHistoryRepository, appCoroutineScope, ioDispatcher, { Clock.System.now() })

    private class Listen(
        val uid: Long,
        val song: Song,
        val startedAt: Instant,
        val context: PlayContext
    ) {
        var listenedMs: Long = 0
        var lastPositionMs: Int? = null
    }

    /** The current item's listen; main thread only, as is everything below. */
    private var listen: Listen? = null

    /** The last listen, ended without a track end, waiting [TRACK_END_GRACE_MS] for one before it's written. */
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
            playbackOperations.progressFlow.collect { progress ->
                val current = listen ?: return@collect
                val position = progress?.position ?: return@collect
                val step = current.lastPositionMs?.let { position - it }
                if (step != null && step in 1..MAX_TICK_MS && playbackOperations.playbackStateFlow.value == PlaybackState.Playing) {
                    current.listenedMs += step
                }
                current.lastPositionMs = position
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { song ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.recordPlayedThrough(song) }
                onTrackEnded(song)
            }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.pausePositionFlow.collect { songPosition ->
                appCoroutineScope.launch(ioDispatcher) { songRepository.setPlaybackPosition(songPosition.song, songPosition.positionMs) }
            }
        }
    }

    private fun onTrackEnded(song: Song) {
        val pending = ended
        val current = listen
        when {
            pending != null && pending.first.song.id == song.id -> {
                pending.second.cancel()
                ended = null
                write(pending.first, completed = true)
            }

            current != null && current.song.id == song.id -> {
                write(current, completed = true)
                // It starts over on repeat, or the queue moves on and the fresh listen is dropped as too short.
                listen = Listen(current.uid, current.song, now(), current.context)
            }
        }
    }

    /** Ends the current listen without a track end: it's written as not completed unless one arrives soon. */
    private fun endListen() {
        val current = listen ?: return
        ended?.let { (previous, job) ->
            job.cancel()
            writeIfListened(previous)
        }
        val job = appCoroutineScope.launch(Dispatchers.Main.immediate) {
            delay(TRACK_END_GRACE_MS)
            ended = null
            writeIfListened(current)
        }
        ended = current to job
    }

    private fun writeIfListened(listen: Listen) {
        if (listen.listenedMs >= PlayHistoryRepository.MIN_LISTENED_MS) write(listen, completed = false)
    }

    private fun write(
        listen: Listen,
        completed: Boolean
    ) {
        val listenedMs = listen.listenedMs
        appCoroutineScope.launch(ioDispatcher) {
            playHistoryRepository.recordPlay(listen.song, listen.startedAt, listenedMs, completed, listen.context)
        }
    }

    companion object {
        /** How long a listen that ended without a track end waits for one. */
        const val TRACK_END_GRACE_MS = 2_000L

        /** The largest step between progress ticks counted as listening (a tick at up to 2x speed, or a late one). */
        const val MAX_TICK_MS = 10_000
    }
}
