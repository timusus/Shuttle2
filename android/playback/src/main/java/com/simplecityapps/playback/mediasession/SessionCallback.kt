package com.simplecityapps.playback.mediasession

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import com.simplecityapps.playback.R
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.androidauto.PlayQueue
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
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
 * [isTrustedCaller] says whether a controller may browse the library and edit the queue: the system, S2 itself, and
 * the callers the app knows (Android Auto). The service is exported, so any other app can connect, and gets the
 * transport controls and requests to play something (a media id, a URI or a search, as the old session had), but
 * not the queue's contents.
 *
 * [carAccess] says whether a car may use the library. A locked car browses only [UPGRADE_ROOT_ID], and is refused the
 * rest of the library, a search and a request to play something, so that nothing reaches the library around the
 * upgrade item.
 */
class SessionCallback(
    private val context: Context,
    private val playRequests: PlayRequests,
    private val mediaIdHelper: MediaIdHelper,
    private val queueOperations: QueueOperations,
    /** The song the saved queue was left on, to offer for resumption before the queue is restored. */
    private val nowPlaying: () -> NowPlayingSnapshot?,
    private val scope: CoroutineScope,
    private val carAccess: CarAccess,
    private val isTrustedCaller: (ControllerInfo) -> Boolean
) : MediaLibrarySession.Callback {
    override fun onConnect(session: MediaSession, controller: ControllerInfo): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailablePlayerCommands(if (isTrustedCaller(controller)) MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS else untrustedPlayerCommands)
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
    fun mediaButtonPreferences(shuffleMode: ShuffleMode, repeatMode: RepeatMode): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(if (shuffleMode == ShuffleMode.On) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(context.getString(if (shuffleMode == ShuffleMode.On) com.simplecityapps.core.R.string.shuffle_on else com.simplecityapps.core.R.string.shuffle_off))
            .setSessionCommand(TOGGLE_SHUFFLE)
            .build(),
        CommandButton.Builder(
            when (repeatMode) {
                RepeatMode.Off -> CommandButton.ICON_REPEAT_OFF
                RepeatMode.All -> CommandButton.ICON_REPEAT_ALL
                RepeatMode.One -> CommandButton.ICON_REPEAT_ONE
            }
        )
            .setDisplayName(
                context.getString(
                    when (repeatMode) {
                        RepeatMode.Off -> com.simplecityapps.core.R.string.repeat_off
                        RepeatMode.All -> com.simplecityapps.core.R.string.repeat_all
                        RepeatMode.One -> com.simplecityapps.core.R.string.repeat_one
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

    /**
     * Moves a connected car between the upgrade item and the library as Android Auto locks or unlocks (a purchase, or
     * the trial ending), without it reconnecting, until [scope] ends: a car subscribed to either root fetches its
     * children again, which [onGetChildren] answers for what the car may now see.
     */
    fun launchRootRefreshes(session: MediaLibrarySession): Job = scope.launch {
        carAccess.lockChanges.collect {
            session.notifyChildrenChanged(UPGRADE_ROOT_ID, Int.MAX_VALUE, null)
            session.notifyChildrenChanged(MediaIdHelper.root.mediaId, Int.MAX_VALUE, null)
        }
    }

    // Browsing

    /** The browse tree's root for a controller allowed to browse; any other gets a root with nothing under it. */
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (!isTrustedCaller(browser)) return Futures.immediateFuture(LibraryResult.ofItem(emptyRoot, params))
        return scope.listenableFuture {
            LibraryResult.ofItem(if (carAccess.mayUseLibrary(browser)) MediaIdHelper.root else upgradeRoot(), params)
        }
    }

    /**
     * Either root's children are the library's for a controller that may use it and the upgrade item for a locked
     * car, so a car on either moves to the other when [launchRootRefreshes] tells it to fetch them again.
     */
    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (!isTrustedCaller(browser) || parentId == EMPTY_ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        val isRoot = parentId == UPGRADE_ROOT_ID || parentId == MediaIdHelper.root.mediaId
        return scope.listenableFuture {
            val children = when {
                carAccess.mayUseLibrary(browser) -> mediaIdHelper.getChildren(if (isRoot) MediaIdHelper.root.mediaId else parentId)
                isRoot -> listOf(upgradeItem())
                else -> emptyList()
            }
            LibraryResult.ofItemList(children.page(page, pageSize), params)
        }
    }

    private fun upgradeRoot(): MediaItem = MediaItem.Builder()
        .setMediaId(UPGRADE_ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
        .build()

    /**
     * Playable, though there's nothing to play: Android Auto may hide an item that's neither browsable nor playable,
     * and playing this one reports [SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED] with the upgrade message
     * ([requireLibrary]) for the car to show. The upgrade happens on the phone, never in the car.
     */
    private fun upgradeItem(): MediaItem = MediaItem.Builder()
        .setMediaId(UPGRADE_ITEM_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(context.getString(R.string.auto_pro_upgrade_title))
                .setSubtitle(context.getString(R.string.auto_pro_upgrade_subtitle))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        if (!isTrustedCaller(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        if (mediaId == UPGRADE_ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItem(upgradeRoot(), null))
        if (mediaId == UPGRADE_ITEM_ID) return Futures.immediateFuture(LibraryResult.ofItem(upgradeItem(), null))
        return scope.listenableFuture {
            if (!carAccess.mayUseLibrary(browser)) return@listenableFuture LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
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
        if (!isTrustedCaller(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return scope.listenableFuture {
            if (!carAccess.mayUseLibrary(browser)) return@listenableFuture LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
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
        if (!isTrustedCaller(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
        return scope.listenableFuture {
            val results = if (carAccess.mayUseLibrary(browser)) mediaIdHelper.search(query) else emptyList()
            LibraryResult.ofItemList(results.page(page, pageSize), params)
        }
    }

    // Playing

    /**
     * Resolves a request to play [mediaItems] (a browsed media id, a URI or a voice search, as the requests from
     * older controllers arrive) to songs, and sets them as the queue through [PlayRequests]. The session then sets
     * the player's own items, which [SessionPlayer] leaves alone, and plays them if that's what was asked. A voice
     * search for nothing in particular leaves a queue there is to resume as it is.
     */
    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.listenableFuture {
        requireLibrary(mediaSession, controller)
        val search = mediaItems.singleOrNull()?.takeIf { item -> item.isSearch }?.requestMetadata
        val playQueue = if (search != null) {
            playRequests.queueForSearch(search.searchQuery, search.extras) ?: return@listenableFuture currentItems(mediaSession.player)
        } else {
            resolve(mediaItems, startIndex)
        }
        if (playQueue == null || playQueue.songs.isEmpty()) {
            throw UnsupportedOperationException("Nothing to play for ${mediaItems.map { it.mediaId }}")
        }
        playRequests.setQueue(playQueue.songs, playQueue.position.coerceAtLeast(0), source = "onSetMediaItems", shuffled = playQueue.shuffled)
        currentItems(mediaSession.player)
    }

    /** Resolves each item to its songs, as [onSetMediaItems] does, tagged as queue entries for [SessionPlayer] to add. */
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = scope.listenableFuture {
        requireLibrary(mediaSession, controller)
        mediaItems.flatMap { item -> songsFor(item) }.map { song -> song.toQueueEntry().toMediaItem() }
    }

    /**
     * Resumes the saved queue (for a Bluetooth headset's play button, or the system's resumption controls after a
     * reboot): the queue is restored as the app starts, so this waits for that and hands back the player's own items.
     * Asked only what it would resume (the system's resumption controls, before anything plays), it answers with the
     * saved song straight away while the queue is still being restored.
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaItemsWithStartPosition> = scope.listenableFuture {
        requireLibrary(mediaSession, controller)
        val queue = queueOperations.queueStateFlow.value
        if (!isForPlayback && !queue.isRestored && queue.items.isEmpty()) {
            nowPlaying()?.let { snapshot ->
                return@listenableFuture MediaItemsWithStartPosition(listOf(snapshot.toSong().toQueueEntry().toMediaItem()), 0, snapshot.positionMs.toLong())
            }
        }
        queueOperations.queueStateFlow.awaitRestored()
        if (mediaSession.player.mediaItemCount == 0) throw UnsupportedOperationException("No saved queue to resume")
        currentItems(mediaSession.player)
    }

    /**
     * Refuses a locked car's request to play something (the upgrade item, a media id it kept from before, a voice
     * search or resuming), telling it why, with the upgrade message, for the car to show.
     */
    private suspend fun requireLibrary(session: MediaSession, controller: ControllerInfo) {
        if (carAccess.mayUseLibrary(controller)) return
        session.sendError(controller, SessionError(SessionError.ERROR_SESSION_PREMIUM_ACCOUNT_REQUIRED, context.getString(R.string.auto_pro_upgrade_subtitle)))
        throw UnsupportedOperationException("Android Auto needs Shuttle Music Pro")
    }

    private suspend fun resolve(mediaItems: List<MediaItem>, startIndex: Int): PlayQueue? {
        val item = mediaItems.singleOrNull()
        if (item != null && item.mediaId != MediaItem.DEFAULT_MEDIA_ID && item.requestMetadata.mediaUri == null) {
            return playRequests.songsForMediaId(item.mediaId)
        }
        if (item != null) return PlayQueue(songsFor(item), 0)
        val songs = mediaItems.map { songsFor(it) }
        val start = startIndex.takeIf { it != C.INDEX_UNSET }?.coerceIn(0, songs.size) ?: 0
        return PlayQueue(songs.flatten(), songs.take(start).sumOf { it.size })
    }

    /** A voice search: an item with a search query, or one that names nothing at all, as a search for "music" arrives. */
    private val MediaItem.isSearch: Boolean
        get() = requestMetadata.searchQuery != null || (mediaId == MediaItem.DEFAULT_MEDIA_ID && requestMetadata.mediaUri == null)

    /**
     * The songs [item] asks for on its own: those a search finds, the file at a URI, or the song a media id names (not
     * the album or playlist it's in). An item that names nothing is a search with no query, which names every song.
     */
    private suspend fun songsFor(item: MediaItem): List<Song> {
        val request = item.requestMetadata
        return when {
            item.isSearch -> playRequests.songsForSearch(request.searchQuery, request.extras)
            request.mediaUri != null -> listOfNotNull(playRequests.songForUri(request.mediaUri!!, mimeType = null))
            else -> listOfNotNull(songFor(item))
        }
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

        /** What a controller that isn't trusted may do: everything but change the queue's contents. */
        private val untrustedPlayerCommands: Player.Commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
            .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .build()

        private const val EMPTY_ROOT_ID = "EMPTY_ROOT"

        /** The root a car gets once Android Auto needs Shuttle Music Pro: one item, pointing the user at the phone. */
        const val UPGRADE_ROOT_ID = "PRO_UPGRADE_ROOT"

        const val UPGRADE_ITEM_ID = "PRO_UPGRADE"

        private val emptyRoot: MediaItem = MediaItem.Builder()
            .setMediaId(EMPTY_ROOT_ID)
            .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
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
