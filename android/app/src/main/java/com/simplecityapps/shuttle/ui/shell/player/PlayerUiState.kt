package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.runtime.Immutable
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow

/** A song in the queue, and the one Now Playing shows when it is the current item. */
@Immutable
data class PlayerSong(
    val uid: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Int,
    val position: QueuePosition,
    /** The artwork model: Glide loads a song's artwork by the song itself. */
    val song: Song,
)

/**
 * Everything the player surfaces show except the playback position, which ticks too often to live
 * here (see [PlayerProgress]).
 *
 * [hasQueue] is null until the queue has been restored (or holds items), so on a cold start the
 * saved player level stands until the first real emission instead of flashing the mini player.
 */
@Immutable
data class PlayerUiState(
    val hasQueue: Boolean?,
    val current: PlayerSong?,
    val items: List<PlayerSong>,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: S2RepeatMode = S2RepeatMode.Off,
    val favourite: Boolean = false,
    val sleepTimerActive: Boolean = false,
    val sleepTimerPlayToEnd: Boolean = false,
    val castAvailable: Boolean = false,
    val seed: ArtworkSeed = ArtworkSeed.None,
) {
    companion object {
        val Unknown = PlayerUiState(hasQueue = null, current = null, items = emptyList())
    }
}

/** The current song's position and duration, in milliseconds. */
@Immutable
data class PlayerProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        val Zero = PlayerProgress(0, 0)
    }
}

/** One-off player outcomes, typed by what happened; the route turns them into snackbars. */
sealed interface PlayerUiEvent {
    /** The queue was cleared; [PlayerActions.undoClearQueue] puts it back. */
    data class QueueCleared(val songCount: Int) : PlayerUiEvent
}

/**
 * What the player surfaces can ask for. The ViewModel implements it; the shell threads one
 * instance down the sheet and pane rather than a dozen lambdas through every level.
 */
interface PlayerActions {
    fun togglePlayback()

    fun skipToNext()

    fun skipToPrevious()

    fun seekTo(positionMs: Long)

    fun toggleShuffle()

    fun cycleRepeatMode()

    fun toggleFavourite()

    fun startSleepTimer(
        durationMs: Long,
        playToEnd: Boolean,
    )

    fun stopSleepTimer()

    /** The sleep timer's time left in milliseconds, ticking while collected: null when off, 0 while it waits for the track to end. */
    fun sleepTimerRemaining(): Flow<Long?>

    fun skipToQueueItem(uid: Long)

    fun moveQueueItem(
        from: Int,
        to: Int,
    )

    fun removeQueueItem(uid: Long)

    fun playNext(uid: Long)

    fun clearQueue()

    fun undoClearQueue()
}
