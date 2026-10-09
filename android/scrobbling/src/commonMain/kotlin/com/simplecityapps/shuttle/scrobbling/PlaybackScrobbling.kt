package com.simplecityapps.shuttle.scrobbling

import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmScrobbler
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzScrobbler
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleQueue
import com.simplecityapps.shuttle.settings.ScrobblingSettings
import dev.zacsweers.metro.Inject
import kotlin.time.Clock
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Feeds the playback flows to a [ScrobblePlanner] and its decisions to [LastFmScrobbler] and [ListenBrainzScrobbler] (#503), on both platforms:
 * Android's ScrobblingInitializer and iOS's AppGraph each call [start] once at launch. Collected on
 * [Dispatchers.Main.immediate] so the planner sees every change in order on one thread; a server song counts as
 * already reported while S2 reports it to its server.
 */
class PlaybackScrobbling
@Inject
constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playbackReporter: AggregatePlaybackReporter,
    private val librarySettings: LibrarySettings,
    private val scrobblingSettings: ScrobblingSettings,
    private val lastFmScrobbler: LastFmScrobbler,
    private val listenBrainzScrobbler: ListenBrainzScrobbler,
    private val scrobbleQueue: ScrobbleQueue,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {
    fun start() {
        val origin = TimeSource.Monotonic.markNow()
        val planner = ScrobblePlanner(
            isServerSong = { song -> playbackReporter.handles(song) && librarySettings.reportPlaybackToServer.value },
            elapsedRealtimeMs = { origin.elapsedNow().inWholeMilliseconds },
            currentTimeMs = { Clock.System.now().toEpochMilliseconds() }
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

            is ScrobblePlanner.Decision.NowPlaying -> {
                appCoroutineScope.launch { lastFmScrobbler.nowPlaying(decision.song) }
                appCoroutineScope.launch { listenBrainzScrobbler.nowPlaying(decision.song) }
            }

            is ScrobblePlanner.Decision.Scrobble -> {
                appCoroutineScope.launch { lastFmScrobbler.scrobble(decision.song, decision.startedAtEpochSec) }
                appCoroutineScope.launch { listenBrainzScrobbler.scrobble(decision.song, decision.startedAtEpochSec) }
            }
        }
    }
}
