package com.simplecityapps.shuttle.ui.widgets

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.coroutines.launchCollectingChanges
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The single path that keeps the home-screen widgets current. Playback state, queue, shuffle and repeat changes
 * request an update; requests are debounced, and each one writes the shared widget state once and redraws every widget.
 */
@Singleton
class WidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: PlaybackOperations,
    private val queueManager: QueueOperations,
    private val artworkStore: WidgetArtworkStore,
    private val preferenceManager: GeneralPreferenceManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) {
    private val updateRequests = Channel<Unit>(Channel.CONFLATED)

    private var updateJob: Job? = null

    private var changesJob: Job? = null

    private var lastConfiguration: Configuration? = null

    /**
     * Redraws the widgets when the system switches between light and dark, or its palette changes.
     *
     * On API 31+ this is belt and braces: the widget hands the launcher both the light and dark background
     * (see `NowPlayingWidget.backgroundColor`), and the launcher picks. Below API 31 colours are resolved when
     * the widget is drawn, so a switch needs a redraw. If the app isn't running, the next update catches up.
     */
    private val configurationCallbacks =
        object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val previous = lastConfiguration
                lastConfiguration = Configuration(newConfig)
                if (previous == null) return
                val nightChanged = (previous.uiMode xor newConfig.uiMode) and Configuration.UI_MODE_NIGHT_MASK != 0
                val paletteChanged = previous.diff(newConfig) and CONFIG_ASSETS_PATHS != 0
                if (nightChanged || paletteChanged) {
                    appCoroutineScope.launch { NowPlayingWidget().updateAll(context) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() {}
        }

    fun registerCallbacks() {
        if (changesJob == null) {
            changesJob =
                appCoroutineScope.launchWidgetUpdateRequests(
                    playbackStateFlow = playbackManager.playbackStateFlow,
                    queueStateFlow = queueManager.queueStateFlow,
                    shuffleModeFlow = queueManager.shuffleModeFlow,
                    repeatModeFlow = queueManager.repeatModeFlow,
                    context = Dispatchers.Main.immediate,
                    onChange = ::requestUpdate
                )
        }
        if (lastConfiguration == null) {
            lastConfiguration = Configuration(context.resources.configuration)
            context.registerComponentCallbacks(configurationCallbacks)
        }
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
        changesJob?.cancel()
        changesJob = null
        context.unregisterComponentCallbacks(configurationCallbacks)
        lastConfiguration = null
        updateJob?.cancel()
        updateJob = null
    }

    fun requestUpdate() {
        updateRequests.trySend(Unit)
    }

    /** Applies a new background opacity, a percentage, to every widget straight away. */
    fun onBackgroundOpacityChanged(opacityPercent: Int) {
        appCoroutineScope.launch {
            val store = NowPlayingWidgetStateDefinition.getDataStore(context, "")
            store.updateData { it.copy(backgroundOpacity = opacityPercent) }
            NowPlayingWidget().updateAll(context)
        }
    }

    private suspend fun updateWidgets() {
        // Until the queue is restored we don't know what's playing, so keep showing the last saved state.
        if (!queueManager.hasRestoredQueue) return
        if (GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java).isEmpty()) return

        val song = queueManager.getCurrentItem()?.song
        if (song == null) {
            publish(NowPlayingWidgetState.Idle.copy(backgroundOpacity = preferenceManager.widgetBackgroundOpacity))
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
        artworkPath = artworkPath,
        backgroundOpacity = preferenceManager.widgetBackgroundOpacity
    )

    private suspend fun publish(state: NowPlayingWidgetState) {
        val store = NowPlayingWidgetStateDefinition.getDataStore(context, "")
        if (store.data.first() == state) return
        store.updateData { state }
        NowPlayingWidget().updateAll(context)
    }

    companion object {
        private const val UPDATE_DEBOUNCE_MS = 150L
        private const val ARTWORK_WAIT_MS = 1000L
        private const val ARTWORK_MAX_WAIT_MS = 10_000L

        /** `ActivityInfo.CONFIG_ASSETS_PATHS`, hidden: set when a theme overlay such as the system palette changes. */
        private const val CONFIG_ASSETS_PATHS = 0x80000000.toInt()
    }
}

/**
 * Calls [onChange] for each change after launch to the playback state, the queue (its contents, current item,
 * shuffle order or restore), the shuffle mode or the repeat mode. Progress ticks aren't watched: the widget
 * doesn't show the position.
 *
 * Each flow is compared against its value when this is called, so the state the caller is about to draw
 * from isn't reported as a change, and a change made before collection starts isn't missed.
 */
internal fun CoroutineScope.launchWidgetUpdateRequests(
    playbackStateFlow: StateFlow<PlaybackState>,
    queueStateFlow: StateFlow<QueueState>,
    shuffleModeFlow: StateFlow<QueueManager.ShuffleMode>,
    repeatModeFlow: StateFlow<QueueManager.RepeatMode>,
    context: CoroutineContext,
    onChange: () -> Unit
): Job {
    val playbackState = playbackStateFlow.value
    val queueState = queueStateFlow.value
    val shuffleMode = shuffleModeFlow.value
    val repeatMode = repeatModeFlow.value
    return launch(context) {
        launchCollectingChanges(playbackStateFlow, playbackState) { _, _ -> onChange() }
        launchCollectingChanges(queueStateFlow, queueState) { _, _ -> onChange() }
        launchCollectingChanges(shuffleModeFlow, shuffleMode) { _, _ -> onChange() }
        launchCollectingChanges(repeatModeFlow, repeatMode) { _, _ -> onChange() }
    }
}

/** How the widget receivers, which Hilt can't inject, reach the [WidgetManager]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetManager(): WidgetManager
}
