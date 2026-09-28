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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Records each song's plays, for Recently Played, Most Played, an artist's Top Songs and the listening history (#633),
 * on both platforms:
 * - a track end ([PlaybackOperations.trackEndedFlow]) counts the song as played through (its play count, last played
 *   and last completed);
 * - a play is written to the listening history as soon as it reaches the song's threshold
 *   ([PlayHistoryRepository.listenThresholdMs]: half the song or 4 minutes, whichever comes first, for a song of 30
 *   seconds or more), so a listen isn't lost when the app is killed mid-song or playback stops on the last one (#651);
 *   its track end then marks it completed. A play that doesn't reach the threshold writes nothing, and moving off a
 *   song writes nothing either;
 * - a pause ([PlaybackOperations.pausePositionFlow]) saves its position (and when it was last played).
 *
 * Each play of the current queue item is a listen: it starts when the item becomes current (or when it starts over on
 * repeat), with the queue's [QueueOperations.playContext] as it stands then. It reaches the threshold when a progress
 * tick while playing is at or past it, having been before it earlier in the listen: a seek past it counts (once), while
 * a song resumed past it (after a restart, say) was counted when it got there before. Its listened time adds up the
 * progress ticks while playing, counting only small steps forward, so a seek isn't listening and a skip back isn't
 * negative. A track end can reach this before or after the queue moves on, so the last listen waits
 * [TRACK_END_GRACE_MS] for one after the queue moves on.
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
            playbackOperations.progressFlow.collect { progress ->
                val current = listen ?: return@collect
                val position = progress?.position ?: return@collect
                val playing = playbackOperations.playbackStateFlow.value == PlaybackState.Playing
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
                complete(pending.first)
            }

            current != null && current.song.id == song.id -> {
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
