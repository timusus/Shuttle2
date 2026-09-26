package com.simplecityapps.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import com.simplecityapps.playback.queue.queueEntryOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Publishes [player]'s progress through the current item, which the player only gives when asked: every
 * [INTERVAL_MS] while [ticking], and whenever [publish] is called (on every jump). Called on the player's thread;
 * [scope] runs there too.
 */
class ProgressTicker(
    private val player: Player,
    private val scope: CoroutineScope
) {
    private val _progressFlow = MutableStateFlow<PlaybackProgress?>(null)

    /** The last published progress; null until the first. */
    val progressFlow: StateFlow<PlaybackProgress?> = _progressFlow.asStateFlow()

    private var job: Job? = null

    /** Whether progress is published every [INTERVAL_MS]. */
    val ticking: Boolean
        get() = job?.isActive == true

    /**
     * Publishes the current position and duration. Until the item is ready, its duration is the one its song is
     * tagged with; with no current item, nothing is published.
     */
    fun publish() {
        if (player.mediaItemCount == 0) return
        val position = player.currentPosition.toInt()
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.toInt() ?: player.currentMediaItem?.queueEntryOrNull?.song?.duration ?: return
        _progressFlow.value = PlaybackProgress(position, duration)
    }

    /** Starts or stops publishing every [INTERVAL_MS]. */
    fun setTicking(ticking: Boolean) {
        if (!ticking) {
            job?.cancel()
            job = null
        } else if (!this.ticking) {
            job =
                scope.launch {
                    while (isActive) {
                        publish()
                        delay(INTERVAL_MS)
                    }
                }
        }
    }

    companion object {
        const val INTERVAL_MS = 100L
    }
}
