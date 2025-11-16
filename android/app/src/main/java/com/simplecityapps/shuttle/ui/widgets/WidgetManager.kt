package com.simplecityapps.shuttle.ui.widgets

import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueWatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

class WidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackWatcher: PlaybackWatcher,
    private val queueWatcher: QueueWatcher
) : PlaybackWatcherCallback,
    QueueChangeCallback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    enum class UpdateReason {
        PlaystateChanged,
        QueueChanged,
        QueuePositionChanged,
        Unknown
    }

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)
    }

    fun removeCallbacks() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)
    }

    fun updateAppWidgets(updateReason: UpdateReason = UpdateReason.Unknown) {
        scope.launch {
            // Update all widgets using Glance API
            Widget41().updateAll(context)
            Widget42().updateAll(context)
        }
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaybackStateChanged(playbackState: PlaybackState) {
        super.onPlaybackStateChanged(playbackState)
        updateAppWidgets(UpdateReason.PlaystateChanged)
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged(reason: QueueChangeCallback.QueueChangeReason) {
        updateAppWidgets(UpdateReason.QueueChanged)
    }

    override fun onQueuePositionChanged(
        oldPosition: Int?,
        newPosition: Int?
    ) {
        super.onQueuePositionChanged(oldPosition, newPosition)
        updateAppWidgets(UpdateReason.QueuePositionChanged)
    }

    companion object {
        const val ARG_UPDATE_REASON = "update_reason"
    }
}
