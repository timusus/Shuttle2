package com.simplecityapps.playback.mediasession

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PlayQueue
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The media session's callback: Android Auto's browse tree and search ([MediaIdHelper]), requests to play a media id,
 * a URI or a voice search ([PlayRequests]), resuming playback from the saved queue, and the notification's shuffle and
 * repeat buttons.
 *
 * A request to play something sets the queue through [QueueOperations] before the session touches the player, then
 * hands the session the player's own items, which [SessionPlayer] recognises and leaves alone.
 *
 * [isKnownCaller] says whether a controller that isn't [trusted][ControllerInfo.isTrusted] may browse the library.
 */
class SessionCallback(
    private val context: Context,
    private val playRequests: PlayRequests,
    private val mediaIdHelper: MediaIdHelper,
    private val queueOperations: QueueOperations,
    private val scope: CoroutineScope,
    private val isKnownCaller: (ControllerInfo) -> Boolean
) : MediaLibrarySession.Callback {
    override fun onConnect(session: MediaSession, controller: ControllerInfo): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(TOGGLE_SHUFFLE)
                .add(TOGGLE_REPEAT)
                .build()
        )
        .setMediaButtonPreferences(mediaButtonPreferences(queueOperations.getShuffleMode(), queueOperations.getRepeatMode()))
        .build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
        TOGGLE_SHUFFLE.customAction -> {
            scope.launch { queueOperations.toggleShuffleMode() }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        TOGGLE_REPEAT.customAction -> {
            queueOperations.toggleRepeatMode()
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        else -> Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    /** The shuffle and repeat buttons, showing the modes they're in. */
    fun mediaButtonPreferences(shuffleMode: QueueManager.ShuffleMode, repeatMode: QueueManager.RepeatMode): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(if (shuffleMode == QueueManager.ShuffleMode.On) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(context.getString(if (shuffleMode == QueueManager.ShuffleMode.On) com.simplecityapps.core.R.string.shuffle_on else com.simplecityapps.core.R.string.shuffle_off))
            .setSessionCommand(TOGGLE_SHUFFLE)
            .build(),
        CommandButton.Builder(
            when (repeatMode) {
                QueueManager.RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF
                QueueManager.RepeatMode.All -> CommandButton.ICON_REPEAT_ALL
                QueueManager.RepeatMode.One -> CommandButton.ICON_REPEAT_ONE
            }
        )
            .setDisplayName(
                context.getString(
                    when (repeatMode) {
                        QueueManager.RepeatMode.Off -> com.simplecityapps.core.R.string.repeat_off
                        QueueManager.RepeatMode.All -> com.simplecityapps.core.R.string.repeat_all
                        QueueManager.RepeatMode.One -> com.simplecityapps.core.R.string.repeat_one
                    }
                )
            )
            .setSessionCommand(TOGGLE_REPEAT)
            .build()
    )

    /** Keeps [session]'s shuffle and repeat buttons showing the modes they're in, until [scope] ends. */
    fun launchMediaButtonUpdates(session: MediaSession): Job = scope.launch {
        combine(queueOperations.shuffleModeFlow, queueOperations.repeatModeFlow, ::Pair)
            .distinctUntilChanged()
            .collect { (shuffleMode, repeatMode) -> session.setMediaButtonPreferences(mediaButtonPreferences(shuffleMode, repeatMode)) }
    }

    // Browsing

    private fun mayBrowse(browser: ControllerInfo): Boolean = browser.isTrusted || isKnownCaller(browser)

    /** The browse tree's root for a controller allowed to browse; any other gets a root with nothing under it. */
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(if (mayBrowse(browser)) MediaIdHelper.root else emptyRoot, params)
    )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!mayBrowse(browser) || parentId == EMPTY_ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        return scope.listenableFuture {
            LibraryResult.ofItemList(mediaIdHelper.getChildren(parentId).page(page, pageSize), params)
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (!mayBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return scope.listenableFuture {
            mediaIdHelper.getItem(mediaId)?.let { item -> LibraryResult.ofItem(item, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    /** Searches, then tells [browser] how many results there are, for it to fetch with [onGetSearchResult]. */
    override fun onSearch(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        if (!mayBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return scope.listenableFuture {
            val results = mediaIdHelper.search(query)
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid(params)
        }
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!mayBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return scope.listenableFuture {
            LibraryResult.ofItemList(mediaIdHelper.search(query).page(page, pageSize), params)
        }
    }

    // Playing

    /**
     * Resolves a request to play [mediaItems] (a browsed media id, a URI or a voice search, as the requests from
     * older controllers arrive) to songs, and sets them as the queue through [PlayRequests]. The session then sets
     * the player's own items, which [SessionPlayer] leaves alone, and plays them if that's what was asked.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.listenableFuture {
        val playQueue = resolve(mediaItems, startIndex)
        if (playQueue == null || playQueue.songs.isEmpty()) {
            throw UnsupportedOperationException("Nothing to play for ${mediaItems.map { it.mediaId }}")
        }
        playRequests.setQueue(playQueue.songs, playQueue.position.coerceAtLeast(0), source = "onSetMediaItems")
        currentItems(mediaSession.player)
    }

    /** Resolves each item to its song, tagged as a queue entry, for [SessionPlayer] to add to the queue. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = scope.listenableFuture {
        mediaItems.mapNotNull { item -> songFor(item) }.map { song -> song.toQueueEntry().toMediaItem() }
    }

    /**
     * Resumes the saved queue (for a Bluetooth headset's play button, or the system's resumption controls after a
     * reboot): the queue is restored as the app starts, so this waits for that and hands back the player's own items.
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.listenableFuture {
        queueOperations.queueStateFlow.awaitRestored()
        if (mediaSession.player.mediaItemCount == 0) throw UnsupportedOperationException("No saved queue to resume")
        currentItems(mediaSession.player)
    }

    private suspend fun resolve(mediaItems: List<MediaItem>, startIndex: Int): PlayQueue? {
        val item = mediaItems.singleOrNull()
        if (item != null) {
            val request = item.requestMetadata
            return when {
                request.searchQuery != null -> PlayQueue(playRequests.songsForSearch(request.searchQuery, request.extras), 0)
                request.mediaUri != null -> playRequests.songForUri(request.mediaUri!!, mimeType = null)?.let { song -> PlayQueue(listOf(song), 0) }
                item.mediaId != MediaItem.DEFAULT_MEDIA_ID -> playRequests.songsForMediaId(item.mediaId)
                else -> null
            }
        }
        return PlayQueue(mediaItems.mapNotNull { songFor(it) }, startIndex.takeIf { it != C.INDEX_UNSET } ?: 0)
    }

    /** The song a playable media id names on its own: the song itself, not the album or playlist it's in. */
    private suspend fun songFor(item: MediaItem): Song? = playRequests.songsForMediaId(item.mediaId)?.let { playQueue -> playQueue.songs.getOrNull(playQueue.position) }

    private fun currentItems(player: Player): MediaItemsWithStartPosition = MediaItemsWithStartPosition(
        List(player.mediaItemCount, player::getMediaItemAt),
        player.currentMediaItemIndex,
        C.TIME_UNSET
    )

    companion object {
        val TOGGLE_SHUFFLE = SessionCommand("com.simplecityapps.shuttle.shuffle", Bundle.EMPTY)
        val TOGGLE_REPEAT = SessionCommand("com.simplecityapps.shuttle.repeat", Bundle.EMPTY)

        private const val EMPTY_ROOT_ID = "EMPTY_ROOT"

        private val emptyRoot: MediaItem = MediaItem.Builder()
            .setMediaId(EMPTY_ROOT_ID)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
            .build()
    }
}

/** The page of this list a paged request asks for. */
internal fun <T> List<T>.page(page: Int, pageSize: Int): ImmutableList<T> {
    if (page < 0 || pageSize <= 0) return ImmutableList.of()
    val from = page.toLong() * pageSize
    if (from >= size) return ImmutableList.of()
    return ImmutableList.copyOf(subList(from.toInt(), minOf(size.toLong(), from + pageSize).toInt()))
}

/** Runs [block] on the main thread, where the session and player live, completing the returned future with its result. */
internal fun <T> CoroutineScope.listenableFuture(block: suspend () -> T): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch(Dispatchers.Main.immediate) {
        try {
            future.set(block())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                future.cancel(false)
            } else {
                Timber.w(e, "Media session request failed")
                future.setException(e)
            }
        }
    }
    future.addListener({ if (future.isCancelled) job.cancel() }, Runnable::run)
    return future
}
