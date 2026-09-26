package com.simplecityapps.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.engine.isResolutionFailure
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * Loads the current item and says when it's ready to play, skipping items that fail to load (their file can't be read,
 * their stream can't be resolved or fetched, their format isn't supported) for the next one. The player neither skips
 * a failed item nor says which item became ready; this does both.
 *
 * Called on the player's thread, and listens to [player] (the one the app plays through). [localPlayer] is the one
 * that says which item an error is for.
 */
class ItemLoader(
    private val player: Player,
    private val localPlayer: ExoPlayer,
    /** Stops playback when a load gives up: nothing more can be skipped to, or the item failed while playing. */
    private val giveUp: () -> Unit
) : Player.Listener {
    /**
     * The uid of the entry that last became ready to play, or that playback moved on to by playing out the one
     * before (it's already buffered); while the current one hasn't, it's loading.
     */
    private var readyUid: Long? = null

    /** The completion of the last [load], called once the item is ready to play or has failed. */
    private var pendingLoad: PendingLoad? = null

    /** Songs skipped in a row because they failed to load. */
    private var loadFailures = 0

    /** Buffered, so a collector on the main thread misses no failure even when two arrive before it resumes. */
    private val _failureFlow = MutableSharedFlow<Song>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each song that fails to play, except one whose stream can't be resolved (a server that can't be reached). */
    val failureFlow: SharedFlow<Song> = _failureFlow.asSharedFlow()

    /** Whether a [load] is waiting for its item to be ready. */
    val isLoading: Boolean
        get() = pendingLoad != null

    /** Whether the current item has been ready to play since it became current. */
    val isCurrentReady: Boolean
        get() = readyUid == player.currentMediaItem?.queueEntryOrNull?.uid

    /**
     * Moves to the current item at [positionMs] and prepares it. [completion] gets whether it loaded at the first
     * attempt once it's ready to play, or the failure if nothing could load (the current item alone, unless
     * [skipUnloadable]: see [onPlayerError]). A later load supersedes it, and it's never called.
     */
    fun load(
        positionMs: Int,
        skipUnloadable: Boolean,
        completion: (Result<Boolean>) -> Unit
    ) {
        pendingLoad = PendingLoad(completion, skipUnloadable)
        readyUid = null
        loadFailures = 0
        player.seekTo(player.currentMediaItemIndex, positionMs.toLong())
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
    }

    /** Fails the pending load, if any: nothing is left to load, as the queue played out. */
    fun abandon() = complete(Result.failure(IllegalStateException("Nothing to load")))

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                readyUid = player.currentMediaItem?.queueEntryOrNull?.uid
                loadFailures = 0
                complete(Result.success(pendingLoad?.attempt == 1))
            }

            Player.STATE_IDLE -> readyUid = null
        }
    }

    /**
     * Playback moving on to the next item (or back to the start on repeat) moves on to an item that's ready, as the
     * player only moves on to an item once it's prepared and raises no new ready state for it, unless the item failed
     * to load: then the player moves on to it to report the failure.
     */
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        val playsOn = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (playsOn && player.playerError == null) {
            readyUid = mediaItem?.queueEntryOrNull?.uid
        }
    }

    /**
     * An item that fails to load is skipped for the next one, up to [MAX_ATTEMPTS] in a row and never past the end of
     * the queue, whether it was loaded directly or reached by playing on. A load that doesn't skip (a restore) leaves
     * it current, paused, unless it's played meanwhile: playing it tries it again, and skips it then. An item that
     * fails once it's playing stops playback.
     */
    override fun onPlayerError(error: PlaybackException) {
        val failedIndex = error.failedIndex() ?: player.currentMediaItemIndex
        val failedEntry = player.currentTimeline.takeIf { failedIndex < it.windowCount }?.let { player.getMediaItemAt(failedIndex).queueEntryOrNull }
        Timber.e(error, "Playback failed for ${failedEntry?.song?.name}")

        if (!error.isResolutionFailure()) {
            failedEntry?.song?.let(_failureFlow::tryEmit)
        }
        val skips = player.playWhenReady || pendingLoad?.skipUnloadable != false
        if (failedEntry != null && failedEntry.uid != readyUid && skips) {
            loadFailures++
            val next = player.currentTimeline.getNextWindowIndex(failedIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (next != C.INDEX_UNSET && loadFailures < MAX_ATTEMPTS) {
                pendingLoad = pendingLoad?.copy(attempt = loadFailures + 1)
                player.seekTo(next, 0)
                player.prepare()
                return
            }
        }
        loadFailures = 0
        complete(Result.failure(error))
        giveUp()
    }

    /** The playlist index of the item [this] failed on, if it says: only the local player does. */
    private fun PlaybackException.failedIndex(): Int? {
        val periodUid = (this as? ExoPlaybackException)?.mediaPeriodId?.periodUid ?: return null
        val timeline = localPlayer.currentTimeline
        val periodIndex = timeline.getIndexOfPeriod(periodUid).takeIf { it != C.INDEX_UNSET } ?: return null
        return timeline.getPeriod(periodIndex, Timeline.Period()).windowIndex
    }

    private fun complete(result: Result<Boolean>) {
        val load = pendingLoad ?: return
        pendingLoad = null
        load.completion(result)
    }

    private data class PendingLoad(
        val completion: (Result<Boolean>) -> Unit,
        val skipUnloadable: Boolean,
        val attempt: Int = 1
    )

    companion object {
        /** How many songs in a row a load tries before giving up on ones that fail to load. */
        const val MAX_ATTEMPTS = 15
    }
}

/** How many events a flow of them buffers for a collector that hasn't caught up. */
internal const val EVENT_BUFFER = 64
