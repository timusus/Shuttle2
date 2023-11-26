package com.simplecityapps.shuttle.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueWatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackWatcher: PlaybackWatcher,
    private val queueWatcher: QueueWatcher
) : PlaybackWatcherCallback, QueueChangeCallback {
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

    fun updateAppWidgets(updateReason: UpdateReason) {
        listOf(
            Intent(context, WidgetProvider41::class.java),
            Intent(context, WidgetProvider42::class.java)
        ).forEach { intent ->
            context.sendBroadcast(
                intent.apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    putExtra(ARG_UPDATE_REASON, updateReason.ordinal)
                }
            )
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
