package com.simplecityapps.playback.widgets

import android.content.Context
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

    enum class UpdateReason {
        PlaystateChanged, QueueChanged, QueuePositionChanged, Unknown
    }

    fun registerCallbacks() {
        playbackWatcher.addCallback(this)
        queueWatcher.addCallback(this)

        updateWidget(UpdateReason.Unknown)
    }

    fun removeCallbacks() {
        playbackWatcher.removeCallback(this)
        queueWatcher.removeCallback(this)
    }

    private fun updateWidget(updateReason: UpdateReason) {
        (context.applicationContext as ActivityIntentProvider).updateAppWidgets(updateReason)
    }

    // PlaybackWatcherCallback Implementation

    override fun onPlaystateChanged(isPlaying: Boolean) {
        super.onPlaystateChanged(isPlaying)

        updateWidget(UpdateReason.PlaystateChanged)
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        super.onQueueChanged()

        updateWidget(UpdateReason.QueueChanged)
    }

    override fun onQueuePositionChanged(oldPosition: Int?, newPosition: Int?) {
        super.onQueuePositionChanged(oldPosition, newPosition)

        updateWidget(UpdateReason.QueuePositionChanged)
    }

    companion object {
        const val ARG_UPDATE_REASON = "update_reason"
    }
}