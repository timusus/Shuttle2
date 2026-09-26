package com.simplecityapps.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import kotlin.math.max

/**
 * The position to resume the current item from, kept in [playbackPreferenceManager] across restarts. It's saved where
 * playback pauses ([savePause]), and when playback comes back from a Cast receiver ([saveHandedBack]); it's the current
 * item's, so it's dropped when another item becomes current by a skip or a queue change, and playing on to the next
 * item saves that item's start instead.
 */
class ResumePositionStore(
    private val player: Player,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    /** Whether playback is moving between devices: the item changes then are the handover's, not a skip. */
    private val isSwitching: () -> Boolean
) : Player.Listener {
    /** The uid of the current entry, as of the last item transition. */
    private var currentUid: Long? = null

    /** The saved position, or null if there's none. */
    val saved: Int?
        get() = playbackPreferenceManager.playbackPosition

    /** Where to resume [song] from: the saved position, else where the song itself says to start ([startOf]). */
    fun resumePosition(song: Song?): Int = saved ?: song?.let(::startOf) ?: 0

    /** Saves where playback paused; with no position, the saved one is cleared, so the next is saved straight away. */
    fun savePause(positionMs: Int?) {
        playbackPreferenceManager.playbackPosition = positionMs
    }

    /**
     * Playback came back from a Cast receiver: saves the position the receiver was at, which the local player now
     * holds, in case the app is gone before playback pauses again. The local position of a receiver that never
     * reported one is kept, and a position of zero is never saved for it.
     */
    fun saveHandedBack() {
        if (player.currentMediaItem?.queueEntryOrNull == null) return
        player.currentPosition.takeIf { it > 0 }?.let { playbackPreferenceManager.playbackPosition = it.toInt() }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        val uid = mediaItem?.queueEntryOrNull?.uid
        val previousUid = currentUid
        currentUid = uid
        if (uid != previousUid && previousUid != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !isSwitching()) {
            playbackPreferenceManager.playbackPosition = null
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        // An item played to its end and the player moved on (to the next item, or back to its start on repeat).
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && oldPosition.mediaItem?.queueEntryOrNull != null) {
            playbackPreferenceManager.playbackPosition = 0
        }
    }

    companion object {
        /** How far back a podcast or audiobook resumes from where it was left, so the listener catches the thread. */
        private const val SPOKEN_REWIND_MS = 5000

        /** Where [song] itself says to start: podcasts and audiobooks a little before where they were left, else 0. */
        fun startOf(song: Song): Int = if (song.type == Song.Type.Podcast || song.type == Song.Type.Audiobook) max(0, song.playbackPosition - SPOKEN_REWIND_MS) else 0
    }
}
