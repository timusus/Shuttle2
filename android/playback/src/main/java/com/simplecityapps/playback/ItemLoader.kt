package com.simplecityapps.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.chromecast.isRemote
import com.simplecityapps.playback.engine.isResolutionFailure
import com.simplecityapps.playback.engine.notFound
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntryOrNull
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.SourceStateStore
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val giveUp: () -> Unit,
    /**
     * Whether an item's URI (a song's own) streams a transcode the server drops when another starts, which a 404 on it
     * re-opens (see [com.simplecityapps.playback.engine.SongUriResolver.isReplaceableTranscode]).
     */
    private val isReplaceableTranscode: (Uri) -> Boolean = { false },
    /** Where a copy that fails for something in itself is marked missing, and unmarked once it plays. */
    private val sourceState: SourceStateStore? = null,
    /** Plays another copy in place of an entry's song, keeping the entry's uid and place (see [com.simplecityapps.playback.queue.QueueFacade.replaceSong]). */
    private val replaceSong: (uid: Long, song: Song) -> Unit = { _, _ -> }
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

    /** The last replaced transcode [reopensReplacedTranscode] opened again, and where. */
    private var lastReopen: Reopen? = null

    /** The uid of the entry whose copies [fallsBackToCopy] has tried, and the song ids it tried. */
    private var triedCopies: Pair<Long, Set<Long>>? = null

    /** Buffered, so a collector on the main thread misses no failure even when two arrive before it resumes. */
    private val _failureFlow = MutableSharedFlow<Song>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each song that fails to play, except one whose stream can't be resolved (a server that can't be reached). */
    val failureFlow: SharedFlow<Song> = _failureFlow.asSharedFlow()

    private val _loadFailure = MutableStateFlow<SongLoadFailure?>(null)

    /**
     * Why the current entry can't play: set when it fails to load and stays current, cleared when it becomes ready,
     * another entry becomes current or the queue is emptied or replaced. Unlike [failureFlow] it includes a stream that
     * can't be resolved.
     */
    val loadFailure: StateFlow<SongLoadFailure?> = _loadFailure.asStateFlow()

    /** Whether a [load] is waiting for its item to be ready. */
    val isLoading: Boolean
        get() = pendingLoad != null

    /** Whether the current item has been ready to play since it became current. */
    val isCurrentReady: Boolean
        get() = readyUid == player.currentMediaItem?.queueEntryOrNull?.uid

    /**
     * Moves to the current item at [positionMs] and prepares it. [completion] gets whether it loaded at the first
     * attempt once it's ready to play, or the failure if nothing could load (the current item alone, unless
     * [skipUnloadable]: see [onPlayerError]). A load still pending is dropped for this one: its completion gets a
     * [CancellationException].
     */
    fun load(
        positionMs: Int,
        skipUnloadable: Boolean,
        completion: (Result<Boolean>) -> Unit
    ) {
        complete(Result.failure(CancellationException("Replaced by a later load")))
        pendingLoad = PendingLoad(completion, skipUnloadable)
        readyUid = null
        loadFailures = 0
        lastReopen = null
        triedCopies = null
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
                val entry = player.currentMediaItem?.queueEntryOrNull
                if (entry != null && entry.uid != readyUid) sourceState?.setSongMissing(entry.song.mediaProvider.name, entry.song.id, missing = false)
                readyUid = entry?.uid
                if (_loadFailure.value?.uid == readyUid) clearLoadFailure()
                loadFailures = 0
                complete(Result.success(pendingLoad?.attempt == 1))
            }

            Player.STATE_IDLE -> readyUid = null
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int
    ) {
        val failedUid = _loadFailure.value?.uid ?: return
        if ((0 until player.mediaItemCount).none { player.getMediaItemAt(it).queueEntryOrNull?.uid == failedUid }) clearLoadFailure()
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
        if (_loadFailure.value?.uid != mediaItem?.queueEntryOrNull?.uid) clearLoadFailure()
        val playsOn = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        if (playsOn && player.playerError == null) {
            readyUid = mediaItem?.queueEntryOrNull?.uid
        }
    }

    /**
     * Playback moving to another position than it was at (a seek, or the player moving on) means a transcode that fails
     * there again is a new failure, not the one [reopensReplacedTranscode] opened again. Opening it again seeks to
     * where it already was, which is no move.
     */
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex || oldPosition.positionMs != newPosition.positionMs) {
            lastReopen = null
        }
    }

    /**
     * An item that fails to load is skipped for the next one, up to [PlaybackPolicy.MAX_LOAD_ATTEMPTS] in a row and never past the end of
     * the queue, whether it was loaded directly or reached by playing on. A load that doesn't skip (a restore) leaves
     * it current, paused, unless it's played meanwhile: playing it tries it again, and skips it then. An item that
     * fails once it's playing stops playback. A transcode the server replaced is opened again rather than either (see
     * [reopensReplacedTranscode]), and a song with another copy plays that copy instead (see [fallsBackToCopy]).
     */
    override fun onPlayerError(error: PlaybackException) {
        val failedIndex = error.failedIndex() ?: player.currentMediaItemIndex
        val failedEntry = player.currentTimeline.takeIf { failedIndex < it.windowCount }?.let { player.getMediaItemAt(failedIndex).queueEntryOrNull }
        if (failedEntry != null && reopensReplacedTranscode(error, failedIndex, failedEntry.uid)) return
        Timber.e(error, "Playback failed for ${failedEntry?.song?.name}")
        if (failedEntry != null && fallsBackToCopy(error, failedIndex, failedEntry)) return

        if (!error.isResolutionFailure()) {
            failedEntry?.song?.let(_failureFlow::tryEmit)
        }
        val skips = player.playWhenReady || pendingLoad?.skipUnloadable != false
        if (failedEntry != null && failedEntry.uid != readyUid && skips) {
            loadFailures++
            val next = player.currentTimeline.getNextWindowIndex(failedIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (next != C.INDEX_UNSET && loadFailures < PlaybackPolicy.MAX_LOAD_ATTEMPTS) {
                pendingLoad = pendingLoad?.copy(attempt = loadFailures + 1)
                player.seekTo(next, 0)
                player.prepare()
                return
            }
        }
        loadFailures = 0
        if (failedEntry != null && failedIndex == player.currentMediaItemIndex) {
            _loadFailure.value = SongLoadFailure(failedEntry.uid, failedEntry.song, error.isConnectionFailure())
        }
        complete(Result.failure(error))
        giveUp()
    }

    private fun clearLoadFailure() {
        _loadFailure.value = null
    }

    /** Reopens the current transcode after a 404: the server drops a transcode when another starts, and the player keeps no buffer to seek back into. */
    private fun reopensReplacedTranscode(
        error: PlaybackException,
        failedIndex: Int,
        uid: Long
    ): Boolean {
        if (failedIndex != player.currentMediaItemIndex || error.notFound() == null) return false
        val uri = player.getMediaItemAt(failedIndex).localConfiguration?.uri ?: return false
        if (!isReplaceableTranscode(uri)) return false
        val reopen = Reopen(uid, player.currentPosition)
        if (reopen == lastReopen) return false
        lastReopen = reopen
        Timber.w(error, "Transcode replaced, opening it again at ${reopen.positionMs} ms")
        player.seekTo(failedIndex, reopen.positionMs)
        player.prepare()
        return true
    }

    /**
     * Marks the failed copy missing when it failed for something in itself, then plays the entry's next copy not yet
     * tried for it nor marked, at the position it failed at. Silent, and not a load failure: those are for when every
     * copy has failed. Local playback only: a Cast receiver's window follows the local queue later.
     */
    private fun fallsBackToCopy(
        error: PlaybackException,
        failedIndex: Int,
        failedEntry: QueueEntry
    ): Boolean {
        val song = failedEntry.song
        if (error.isCopyMissing()) sourceState?.setSongMissing(song.mediaProvider.name, song.id, missing = true)
        if (player.isRemote) return false
        val tried = triedCopies?.takeIf { it.first == failedEntry.uid }?.second.orEmpty() + song.id
        triedCopies = failedEntry.uid to tried
        val next = song.copies.firstOrNull { copy ->
            copy.id !in tried && sourceState?.missingSongs(copy.mediaProvider.name)?.contains(copy.id) != true
        } ?: return false
        val positionMs = if (failedIndex == player.currentMediaItemIndex) player.currentPosition else 0
        Timber.w("Playing ${song.name} from ${next.mediaProvider} instead")
        replaceSong(failedEntry.uid, next.copy(copies = song.copies))
        player.seekTo(failedIndex, positionMs)
        player.prepare()
        return true
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

    private data class Reopen(
        val uid: Long,
        val positionMs: Long
    )
}

/** Whether this failed for want of a connection to the server (or to resolve its stream), not for anything in the song. */
private fun Throwable.isConnectionFailure(): Boolean = generateSequence(this) { it.cause }.any { it is ConnectException || it is UnknownHostException || it is SocketTimeoutException || it is NoRouteToHostException }

/** Whether this failed for something in the copy itself: its file gone, its format unplayable, or its server no longer holding it. */
private fun PlaybackException.isCopyMissing(): Boolean = !isConnectionFailure() &&
    (
        errorCode in COPY_MISSING_ERRORS ||
            generateSequence<Throwable>(this) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().any { it.responseCode == 404 || it.responseCode == 410 }
        )

private val COPY_MISSING_ERRORS = setOf(
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
)

/** How many events a flow of them buffers for a collector that hasn't caught up. */
internal const val EVENT_BUFFER = 64
