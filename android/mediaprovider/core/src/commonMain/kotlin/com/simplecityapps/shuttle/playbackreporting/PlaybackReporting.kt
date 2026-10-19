package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackStateSource
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.MainImmediateDispatcher
import dev.zacsweers.metro.Inject
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Feeds playback changes to [PlaybackReportPlanner]; collected on Main so the planner sees them in order. */
class PlaybackReporting
@Inject
constructor(
    private val playbackStateSource: PlaybackStateSource,
    private val queueOperations: QueueOperations,
    private val playbackReporter: AggregatePlaybackReporter,
    private val sender: PlaybackReportSender,
    private val librarySettings: LibrarySettings,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @MainImmediateDispatcher private val mainImmediateDispatcher: CoroutineDispatcher
) {
    private val clock = TimeSource.Monotonic.markNow()
    private var started = false

    /** Starts reporting once: a second call is a no-op, rather than re-sending the current state as a new play. */
    fun start() {
        if (started) return
        started = true

        val planner = PlaybackReportPlanner(isReportable = playbackReporter::handles)

        val enabledFlow = librarySettings.reportPlaybackToServer.stateIn(appCoroutineScope)
        val enabled = enabledFlow.value
        val queueState = queueOperations.queueStateFlow.value
        val play = playbackStateSource.playFlow.value
        val playbackState = playbackStateSource.playbackStateFlow.value
        val progress = playbackStateSource.progressFlow.value
        sender.send(planner.onCurrentItemChanged(queueState))
        sender.send(planner.onPlayChanged(play, now()))
        sender.send(planner.onStateChanged(playbackState.toPlannerState(), now()))
        progress?.let { sender.send(planner.onProgress(it.position, now())) }
        // Enabled last: a song already playing starts only once the planner knows its position.
        sender.send(planner.onEnabledChanged(enabled, now()))

        appCoroutineScope.launchCollectingChanges(enabledFlow, enabled, mainImmediateDispatcher) { _, current ->
            sender.send(planner.onEnabledChanged(current, now()))
        }
        appCoroutineScope.launchCollectingChanges(queueOperations.queueStateFlow, queueState, mainImmediateDispatcher) { _, current ->
            sender.send(planner.onCurrentItemChanged(current))
        }
        appCoroutineScope.launchCollectingChanges(playbackStateSource.playFlow, play, mainImmediateDispatcher) { _, current ->
            sender.send(planner.onPlayChanged(current, now()))
        }
        appCoroutineScope.launchCollectingChanges(playbackStateSource.playbackStateFlow, playbackState, mainImmediateDispatcher) { _, current ->
            sender.send(planner.onStateChanged(current.toPlannerState(), now()))
        }
        appCoroutineScope.launchCollectingChanges(playbackStateSource.progressFlow, progress, mainImmediateDispatcher) { _, current ->
            current?.let { sender.send(planner.onProgress(it.position, now())) }
        }
        appCoroutineScope.launch(mainImmediateDispatcher) {
            playbackStateSource.trackEndedFlow.collect { end -> sender.send(planner.onTrackEnded(end.song)) }
        }

        sender.replayPendingPlays()
    }

    private fun PlaybackReportPlanner.onCurrentItemChanged(queueState: QueueState) = onCurrentItemChanged(queueState.currentItem?.uid, queueState.currentItem?.song, now())

    private fun PlaybackState.toPlannerState() = when (this) {
        PlaybackState.Loading -> PlaybackReportPlanner.State.Loading
        PlaybackState.Playing -> PlaybackReportPlanner.State.Playing
        PlaybackState.Paused -> PlaybackReportPlanner.State.Paused
    }

    private fun now(): Long = clock.elapsedNow().inWholeMilliseconds
}
