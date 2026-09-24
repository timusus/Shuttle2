package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.Call
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Sends [PlaybackReportPlanner]'s calls to [reporter] one at a time, in order, on [scope], so
 * reporting never blocks or fails playback. Each call has [timeoutMs] to succeed, and a failure is
 * dropped: a missed start or progress only leaves the server's now playing briefly out of date.
 *
 * The exception is a track that played through, whose play would be lost: it goes to [pendingPlays]
 * and is recorded through [PlaybackReporter.markPlayed] on the next [replayPendingPlays] (app start),
 * or once a call succeeds after one failed (the server is reachable again).
 *
 * Pending plays aren't replayed while [isEnabled] is false. Whether a call is sent at all is the
 * planner's decision, so the stop it makes when reporting is turned off still goes out.
 */
class PlaybackReportSender(
    private val reporter: PlaybackReporter,
    private val pendingPlays: PendingPlays,
    private val findSongs: suspend (songIds: List<Long>) -> List<Song>,
    private val isEnabled: () -> Boolean,
    private val now: () -> Instant,
    scope: CoroutineScope,
    private val timeoutMs: Long = TIMEOUT_MS
) {
    private sealed interface Work {
        data class Report(val call: Call) : Work

        data object ReplayPendingPlays : Work
    }

    private val work = Channel<Work>(Channel.UNLIMITED)

    // Set by a failed call, so the next successful one knows the server just became reachable again.
    private var failedSinceReplay = false

    init {
        scope.launch {
            for (item in work) {
                when (item) {
                    is Work.Report -> report(item.call)
                    Work.ReplayPendingPlays -> recordPendingPlays()
                }
            }
        }
    }

    fun send(calls: List<Call>) {
        calls.forEach { call -> work.trySend(Work.Report(call)) }
    }

    fun replayPendingPlays() {
        work.trySend(Work.ReplayPendingPlays)
    }

    private suspend fun report(call: Call) {
        val succeeded = attempt {
            when (call) {
                is Call.Start -> reporter.start(call.session, call.positionMs)
                is Call.Progress -> reporter.progress(call.session, call.positionMs, call.paused)
                is Call.Stop -> reporter.stop(call.session, call.positionMs)
            }
        }
        when {
            succeeded && failedSinceReplay -> recordPendingPlays()

            !succeeded -> {
                failedSinceReplay = true
                if (call is Call.Stop && call.playedThrough) {
                    pendingPlays.add(PendingPlays.Play(call.session.song.id, now()))
                }
            }
        }
    }

    private suspend fun recordPendingPlays() {
        if (!isEnabled()) return
        failedSinceReplay = false
        val plays = pendingPlays.all().takeIf { it.isNotEmpty() } ?: return
        val songs = findSongs(plays.map { play -> play.songId }.distinct()).associateBy { song -> song.id }
        // A song that's gone from the library can't be reported any more, so its play is dropped too.
        val done = plays.filter { play ->
            val song = songs[play.songId] ?: return@filter true
            attempt { reporter.markPlayed(song, play.playedAt) }
        }
        pendingPlays.remove(done)
    }

    private suspend fun attempt(call: suspend () -> Boolean): Boolean = try {
        withTimeoutOrNull(timeoutMs) { call() } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Playback report failed")
        false
    }

    companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
