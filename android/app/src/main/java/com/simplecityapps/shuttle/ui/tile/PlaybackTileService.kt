package com.simplecityapps.shuttle.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.appGraph
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class PlaybackTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null

    @Inject
    lateinit var playbackOperations: PlaybackOperations

    @Inject
    lateinit var queueOperations: QueueOperations

    @ContributesTo(AppScope::class)
    interface Injector {
        fun inject(service: PlaybackTileService)
    }

    override fun onCreate() {
        super.onCreate()
        applicationContext.appGraph<Injector>().inject(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        listenJob =
            scope.launch {
                combine(playbackOperations.playbackStateFlow, queueOperations.queueStateFlow) { playbackState, queueState ->
                    PlaybackTileState.from(
                        playbackState = playbackState,
                        currentTitle = queueState.currentItem?.song?.name,
                        hasQueue = queueState.items.isNotEmpty(),
                        isRestored = queueState.isRestored
                    )
                }.collect { render(it) }
            }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        // Read at tap time: the rendered state may not have arrived yet on a cold tap.
        val queue = queueOperations.queueStateFlow.value
        when (PlaybackTileState.tapAction(hasQueue = queue.items.isNotEmpty(), isRestored = queue.isRestored)) {
            // Through the service so playback starts in the foreground, with its notification.
            PlaybackTileState.TapAction.TogglePlayback -> PlaybackService.startAction(this, PlaybackService.ACTION_TOGGLE_PLAYBACK)

            PlaybackTileState.TapAction.OpenApp -> openApp()
        }
    }

    private fun render(state: PlaybackTileState) {
        val tile = qsTile ?: return
        tile.state = if (state.isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_play_pause_24dp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state.subtitle
        }
        tile.updateTile()
    }

    private fun openApp() {
        // The launcher intent, so the tile stays clear of the activity class (ui/** may not import app-only types).
        val intent =
            (
                packageManager.getLaunchIntentForPackage(packageName) ?: run {
                    Timber.w("No launch intent for $packageName; falling back to an explicit launcher intent")
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
                }
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
