package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.os.SystemClock
import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportSender
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reports playback of Jellyfin, Emby and Plex songs to their server (#96): feeds the queue and
 * playback flows to a [PlaybackReportPlanner] and hands its calls to [PlaybackReportSender]. Collected
 * on [Dispatchers.Main.immediate], like [PlaybackInitializer], so the planner sees every change in order
 * on one thread.
 */
class PlaybackReportingInitializer
@Inject
constructor(
    private val playbackOperations: PlaybackOperations,
    private val queueOperations: QueueOperations,
    private val playbackReporter: AggregatePlaybackReporter,
    private val sender: PlaybackReportSender,
    private val librarySettings: LibrarySettings,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : AppInitializer {
    override fun init(application: Application) {
        val planner = PlaybackReportPlanner(
            isReportable = playbackReporter::handles,
            newSessionId = { UUID.randomUUID().toString() }
        )

        val enabledFlow = librarySettings.reportPlaybackToServer.stateIn(appCoroutineScope)
        val enabled = enabledFlow.value
        val queueState = queueOperations.queueStateFlow.value
        val playbackState = playbackOperations.playbackStateFlow.value
        val progress = playbackOperations.progressFlow.value
        sender.send(planner.onCurrentItemChanged(queueState))
        sender.send(planner.onStateChanged(playbackState.toPlannerState(), now()))
        progress?.let { sender.send(planner.onProgress(it.position, now())) }
        // Enabled last: a song already playing starts only once the planner knows its position.
        sender.send(planner.onEnabledChanged(enabled, now()))

        appCoroutineScope.launchCollectingChanges(enabledFlow, enabled, Dispatchers.Main.immediate) { _, current ->
            sender.send(planner.onEnabledChanged(current, now()))
        }
        appCoroutineScope.launchCollectingChanges(queueOperations.queueStateFlow, queueState, Dispatchers.Main.immediate) { _, current ->
            sender.send(planner.onCurrentItemChanged(current))
        }
        appCoroutineScope.launchCollectingChanges(playbackOperations.playbackStateFlow, playbackState, Dispatchers.Main.immediate) { _, current ->
            sender.send(planner.onStateChanged(current.toPlannerState(), now()))
        }
        appCoroutineScope.launchCollectingChanges(playbackOperations.progressFlow, progress, Dispatchers.Main.immediate) { _, current ->
            current?.let { sender.send(planner.onProgress(it.position, now())) }
        }
        appCoroutineScope.launch(Dispatchers.Main.immediate) {
            playbackOperations.trackEndedFlow.collect { song -> sender.send(planner.onTrackEnded(song)) }
        }

        sender.replayPendingPlays()
    }

    private fun PlaybackReportPlanner.onCurrentItemChanged(queueState: QueueState) = onCurrentItemChanged(queueState.currentItem?.uid, queueState.currentItem?.song, now())

    private fun PlaybackState.toPlannerState() = when (this) {
        PlaybackState.Loading -> PlaybackReportPlanner.State.Loading
        PlaybackState.Playing -> PlaybackReportPlanner.State.Playing
        PlaybackState.Paused -> PlaybackReportPlanner.State.Paused
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
