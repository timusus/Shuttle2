package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.runtime.Immutable
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.common.PendingEvent
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
    /** The artwork model: Coil loads a song's artwork by the song itself. */
    val song: Song,
)

/**
 * What Now Playing's bar opens below the transport (app-shell.md, section 1): the queue, the sleep
 * timer, or Playback & sound. With none open the queue still follows the transport, so a drag up
 * reveals it.
 */
enum class NowPlayingPanel { Queue, SleepTimer, PlaybackSound }

/**
 * Everything the player surfaces show except the playback position, which ticks too often to live
 * here (see [PlayerScreenState]).
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
    /** The playback speed, 1 being normal; the pitch stays the same at any speed. */
    val playbackSpeed: Float = 1f,
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    /** The panel the bar has open, or null at rest. */
    val panel: NowPlayingPanel? = null,
) {
    companion object {
        val Unknown = PlayerUiState(hasQueue = null, current = null, items = emptyList())
    }
}

/**
 * The player's whole state, [PlayerViewModel]'s uiState: what the player surfaces show, and the
 * position, which ticks ten times a second while playing. The two are kept apart so the route hands
 * the surfaces a [player] that is the same instance from tick to tick, and only the seek bar and the
 * mini player's bar read [progress] (UDF doc, principle 6).
 */
data class PlayerScreenState(
    val player: PlayerUiState,
    val progress: PlayerProgress,
    val events: List<PendingEvent<PlayerUiEvent>> = emptyList(),
)

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

    /** A row left the queue; [PlayerActions.undoRemoveQueueItem] puts it back. */
    data object QueueItemRemoved : PlayerUiEvent

    /** A song action ran; the shell shows its message, opens its screen or runs its snackbar's action. */
    data class MediaActionDone(val result: MediaActionResult) : PlayerUiEvent

    /** A queued server song was skipped because streaming it needs S2 Pro (monetisation.md, line 136). */
    data class ServerSongSkipped(val songTitle: String) : PlayerUiEvent
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

    fun setPlaybackSpeed(speed: Float)

    fun setReplayGainMode(mode: ReplayGainMode)

    /** Opens [panel], or closes it if it is already open. */
    fun togglePanel(panel: NowPlayingPanel)

    /** Opens [panel], or closes whichever is open when null. */
    fun showPanel(panel: NowPlayingPanel?)

    fun skipToQueueItem(uid: Long)

    /**
     * Moves the row [uid] to just after the row [afterUid], or to the top when null. Identities rather
     * than indices, so a queue that changed during the drag still moves the right row.
     */
    fun moveQueueItem(
        uid: Long,
        afterUid: Long?,
    )

    fun removeQueueItem(uid: Long)

    fun undoRemoveQueueItem()

    fun playNext(uid: Long)

    fun clearQueue()

    fun undoClearQueue()

    /** The shared song actions the player's menus offer for [song], in display order. */
    fun songActions(song: Song): Flow<List<MediaActionType>>

    /** The playlists Add to playlist can add to. */
    fun playlists(): Flow<List<Playlist>>

    /** Runs a song action, or a snackbar's action sent back; the outcome arrives as [PlayerUiEvent.MediaActionDone]. */
    fun onMediaAction(action: MediaAction)
}
