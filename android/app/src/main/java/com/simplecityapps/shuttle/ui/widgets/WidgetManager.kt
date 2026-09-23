package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single path that keeps the home-screen widgets current. Playback and queue callbacks request an update;
 * requests are debounced, and each one writes the shared widget state once and redraws every widget.
 */
@Singleton
class WidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackWatcher: PlaybackWatcher,
    private val queueWatcher: QueueWatcher,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val artworkStore: WidgetArtworkStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : PlaybackWatcherCallback,
    QueueChangeCallback {
    private val updateRequests = Channel<Unit>(Channel.CONFLATED)

    private var updateJob: Job? = null

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)
        if (updateJob == null) {
            updateJob =
                appCoroutineScope.launch {
                    updateRequests.receiveAsFlow().collectLatest {
                        // A new request cancels this one mid-delay, so a burst of callbacks becomes one update.
                        delay(UPDATE_DEBOUNCE_MS)
                        updateWidgets()
                    }
                }
        }
        requestUpdate()
    }

    fun removeCallbacks() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)
        updateJob?.cancel()
        updateJob = null
    }

    fun requestUpdate() {
        updateRequests.trySend(Unit)
    }

    private suspend fun updateWidgets() {
        // Until the queue is restored we don't know what's playing, so keep showing the last saved state.
        if (!queueManager.hasRestoredQueue) return
        if (GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java).isEmpty()) return

        val song = queueManager.getCurrentItem()?.song
        if (song == null) {
            publish(NowPlayingWidgetState.Idle)
            artworkStore.prune(emptyList())
            return
        }

        coroutineScope {
            val artwork = async { artworkStore.artworkPath(song) }
            // Give the artwork a moment so the track and its art usually change together, but don't hold the
            // new title back on a slow load.
            val artworkPath = withTimeoutOrNull(ARTWORK_WAIT_MS) { artwork.await() }
            publish(stateFor(song, artworkPath))
            if (artworkPath == null) {
                // A load that never calls back mustn't hold up every later update, so give up eventually.
                withTimeoutOrNull(ARTWORK_MAX_WAIT_MS) { artwork.await() }?.let { publish(stateFor(song, it)) }
                artwork.cancel()
            }
        }

        // Have the next track's artwork ready, so skipping shows it straight away.
        val next = queueManager.getNext(ignoreRepeat = true)?.song
        next?.let { withTimeoutOrNull(ARTWORK_MAX_WAIT_MS) { artworkStore.artworkPath(it) } }
        artworkStore.prune(listOfNotNull(song, next))
    }

    private fun stateFor(
        song: Song,
        artworkPath: String?
    ) = nowPlayingWidgetState(
        song = song,
        playbackState = playbackManager.playbackState(),
        shuffleMode = queueManager.getShuffleMode(),
        repeatMode = queueManager.getRepeatMode(),
        artworkPath = artworkPath
    )

    private suspend fun publish(state: NowPlayingWidgetState) {
        val store = NowPlayingWidgetStateDefinition.getDataStore(context, "")
        if (store.data.first() == state) return
        store.updateData { state }
        NowPlayingWidget().updateAll(context)
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        requestUpdate()
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        requestUpdate()
    }

    override fun onQueuePositionChanged(
        oldPosition: Int?,
        newPosition: Int?
    ) {
        requestUpdate()
    }

    override fun onShuffleChanged(shuffleMode: QueueManager.ShuffleMode) {
        requestUpdate()
    }

    override fun onRepeatChanged(repeatMode: QueueManager.RepeatMode) {
        requestUpdate()
    }

    override fun onQueueRestored() {
        requestUpdate()
    }

    companion object {
        private const val UPDATE_DEBOUNCE_MS = 150L
        private const val ARTWORK_WAIT_MS = 1000L
        private const val ARTWORK_MAX_WAIT_MS = 10_000L
    }
}

/** How the widget receivers, which Hilt can't inject, reach the [WidgetManager]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetManager(): WidgetManager
}
