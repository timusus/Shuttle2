package com.simplecityapps.playback.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.playback.ActivityIntentProvider
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PlaybackWatcherCallback
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueWatcher
import javax.inject.Inject

class WidgetManager @Inject constructor(
    private val context: Context,
    private val playbackWatcher: PlaybackWatcher,
    private val queueWatcher: QueueWatcher
) : PlaybackWatcherCallback, QueueChangeCallback {

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        updateWidget()
    }

    fun removeCallbacks() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)
    }

    private fun updateWidget() {
        context.sendBroadcast((context.applicationContext as ActivityIntentProvider).provideAppWidgetIntent().apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        })
    }

    // PlaybackWatcherCallback Implementation

    override fun onProgressChanged(position: Int, total: Int, fromUser: Boolean) {
        super.onProgressChanged(position, total, fromUser)
    }

    override fun onPlaystateChanged(isPlaying: Boolean) {
        super.onPlaystateChanged(isPlaying)

        updateWidget()
    }

    override fun onPlaybackComplete(song: Song) {
        super.onPlaybackComplete(song)

        updateWidget()
    }


    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        super.onQueueChanged()

        updateWidget()
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        super.onQueuePositionChanged(oldPosition, newPosition)

        updateWidget()
    }

    override fun onShuffleChanged() {
        super.onShuffleChanged()
    }

    override fun onRepeatChanged() {
        super.onRepeatChanged()
    }
}