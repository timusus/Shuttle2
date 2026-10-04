package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.os.SystemClock
import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.scrobbling.ScrobblePlanner
import com.simplecityapps.shuttle.scrobbling.ScrobblingSettings
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbler
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feeds the playback flows to a [ScrobblePlanner] and its decisions to [LastFmScrobbler] (#503). Collected on
 * [Dispatchers.Main.immediate] so the planner sees every change in order on one thread; a server song counts as
 * already reported while S2 reports it to its server.
 */
class ScrobblingInitializer
@Inject
constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playbackReporter: AggregatePlaybackReporter,
    private val librarySettings: LibrarySettings,
    private val scrobblingSettings: ScrobblingSettings,
    private val lastFmScrobbler: LastFmScrobbler,
    private val scrobbleQueue: ScrobbleQueue,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        val planner = ScrobblePlanner(
            isServerSong = { song -> playbackReporter.handles(song) && librarySettings.reportPlaybackToServer.value },
            elapsedRealtimeMs = SystemClock::elapsedRealtime,
            currentTimeMs = System::currentTimeMillis
        )

        val queueState = queueOperations.queueStateFlow.value
        val playbackState = playbackOperations.playbackStateFlow.value
        val progress = playbackOperations.progressFlow.value
        val speed = playbackOperations.playbackSpeedFlow.value
        val serverStreamsFlow = scrobblingSettings.scrobbleServerStreams.stateIn(appCoroutineScope)
        val serverStreams = serverStreamsFlow.value

        // The state a song is already playing in, replayed in order so it starts only once the planner knows its position.
        planner.onScrobbleServerStreamsChanged(serverStreams)
        planner.onPlaybackSpeedChanged(speed)
        handle(planner.onCurrentItemChanged(queueState.currentItem))
        handle(planner.onStateChanged(playbackState))
        progress?.let { handle(planner.onProgress(it.position)) }

        appCoroutineScope.launchCollectingChanges(serverStreamsFlow, serverStreams, Dispatchers.Main.immediate) { _, current ->
            handle(planner.onScrobbleServerStreamsChanged(current))
        }
        appCoroutineScope.launchCollectingChanges(playbackOperations.playbackSpeedFlow, speed, Dispatchers.Main.immediate) { _, current ->
            planner.onPlaybackSpeedChanged(current)
        }
        appCoroutineScope.launchCollectingChanges(queueOperations.queueStateFlow, queueState, Dispatchers.Main.immediate) { _, current ->
            handle(planner.onCurrentItemChanged(current.currentItem))
        }
        appCoroutineScope.launchCollectingChanges(playbackOperations.playbackStateFlow, playbackState, Dispatchers.Main.immediate) { _, current ->
            handle(planner.onStateChanged(current))
        }
        appCoroutineScope.launchCollectingChanges(playbackOperations.progressFlow, progress, Dispatchers.Main.immediate) { _, current ->
            current?.let { handle(planner.onProgress(it.position)) }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { end -> handle(planner.onTrackEnded(end.song)) }
        }

        // Anything queued while it couldn't send, before the app last closed.
        scrobbleQueue.scheduleFlush()
    }

    private fun handle(decision: ScrobblePlanner.Decision?) {
        when (decision) {
            null -> Unit
            is ScrobblePlanner.Decision.NowPlaying -> appCoroutineScope.launch { lastFmScrobbler.nowPlaying(decision.song) }
            is ScrobblePlanner.Decision.Scrobble -> appCoroutineScope.launch { lastFmScrobbler.scrobble(decision.song, decision.startedAtEpochSec) }
        }
    }
}
