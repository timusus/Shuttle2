package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.ui.actions.MediaActionResult.Message
import javax.inject.Inject

/**
 * Carries out a [MediaAction] and says what the UI should do next. A ViewModel delegates to it and hands the
 * result to its screen as a consumable UiState event:
 *
 * ```
 * fun onMediaAction(action: MediaAction) {
 *     viewModelScope.launch { events.post(mediaActionHandler.handle(action)) }
 * }
 * ```
 *
 * A snackbar's button or a confirmation's "yes" sends its [MediaAction] back through the same call.
 */
class MediaActionHandler @Inject constructor(
    private val resolveSongs: ResolveSongs,
    private val playSongs: PlaySongs,
    private val shuffleSongs: ShuffleSongs,
    private val enqueueSongs: EnqueueSongs,
    private val addToPlaylist: AddToPlaylist,
    private val createPlaylist: CreatePlaylist,
    private val favouriteSongs: FavouriteSongs,
    private val findGoToTarget: FindGoToTarget,
    private val shareSongs: ShareSongs,
    private val excludeSongs: ExcludeSongs,
    private val deleteSongs: DeleteSongs,
    private val downloadSongs: DownloadSongs,
    private val removeFromPlaylist: RemoveFromPlaylist,
    private val restorePlaylistSongs: RestorePlaylistSongs,
) {
    suspend fun handle(action: MediaAction): MediaActionResult = when (action) {
        is MediaAction.Play -> play(action)

        is MediaAction.Shuffle -> shuffle(action)

        is MediaAction.PlayNext -> enqueue(action.selection, EnqueueSongs.Position.Next)

        is MediaAction.AddToQueue -> enqueue(action.selection, EnqueueSongs.Position.End)

        is MediaAction.AddToPlaylist -> addToPlaylist(action)

        is MediaAction.CreatePlaylist -> createPlaylist(action)

        is MediaAction.Favourite -> favourite(action)

        is MediaAction.GoToAlbum -> goTo(action.selection, FindGoToTarget.Destination.Album)

        is MediaAction.GoToArtist -> goTo(action.selection, FindGoToTarget.Destination.AlbumArtist)

        is MediaAction.EditTags -> editTags(action)

        is MediaAction.SongInfo -> songInfo(action)

        is MediaAction.Share -> shareSongs(action.selection)?.let { MediaActionResult.Share(it) } ?: Message(MediaActionMessage.NoSongs)

        is MediaAction.Exclude -> exclude(action)

        is MediaAction.Include -> {
            excludeSongs(action.selection, excluded = false)
            MediaActionResult.None
        }

        is MediaAction.RemoveFromPlaylist -> {
            removeFromPlaylist(action.playlist, action.entries)
            Message(
                MediaActionMessage.RemovedFromPlaylist(action.playlist.name, action.entries.size),
                SnackbarAction(SnackbarAction.Label.Undo, MediaAction.RestoreToPlaylist(action.playlist, action.entries, action.before)),
            )
        }

        is MediaAction.RestoreToPlaylist -> {
            restorePlaylistSongs(action.playlist, action.entries, action.before)
            MediaActionResult.None
        }

        is MediaAction.Delete -> delete(action)

        is MediaAction.Download -> download(action.selection)

        is MediaAction.RemoveDownload -> removeDownload(action.selection)
    }

    private suspend fun play(action: MediaAction.Play): MediaActionResult {
        val songs = resolveSongs(action.selection)
        if (songs.isEmpty()) return Message(MediaActionMessage.NoSongs)
        return when (val result = playSongs(songs, action.position.coerceIn(0, songs.lastIndex))) {
            is PlaySongs.Result.Success -> MediaActionResult.None
            is PlaySongs.Result.Failure -> Message(MediaActionMessage.PlaybackFailed(result.message))
        }
    }

    private suspend fun shuffle(action: MediaAction.Shuffle): MediaActionResult {
        val songs = resolveSongs(action.selection)
        if (songs.isEmpty()) return Message(MediaActionMessage.NoSongs)
        return when (val result = shuffleSongs(songs)) {
            is ShuffleSongs.Result.Success -> MediaActionResult.None
            is ShuffleSongs.Result.Failure -> Message(MediaActionMessage.PlaybackFailed(result.message))
        }
    }

    private suspend fun enqueue(selection: MediaSelection, position: EnqueueSongs.Position): MediaActionResult {
        val songs = enqueueSongs(selection, position)
        return Message(if (songs.isEmpty()) MediaActionMessage.NoSongs else MediaActionMessage.AddedToQueue(songs.size))
    }

    private suspend fun addToPlaylist(action: MediaAction.AddToPlaylist): MediaActionResult = when (
        val result = addToPlaylist(action.playlist, action.selection, action.ignoreDuplicates)
    ) {
        is AddToPlaylist.Result.Success -> Message(MediaActionMessage.AddedToPlaylist(result.playlist.name, result.songs.size))

        is AddToPlaylist.Result.DuplicatesFound -> Message(
            MediaActionMessage.AlreadyInPlaylist(result.playlist.name, result.duplicates.size),
            SnackbarAction(SnackbarAction.Label.AddAnyway, action.copy(ignoreDuplicates = true)),
        )

        is AddToPlaylist.Result.Failure -> Message(
            if (result.message == null) MediaActionMessage.NoSongs else MediaActionMessage.AddToPlaylistFailed(result.message),
        )
    }

    private suspend fun favourite(action: MediaAction.Favourite): MediaActionResult {
        val songs = favouriteSongs(action.selection, action.favourite)
        return when {
            songs.isEmpty() -> Message(MediaActionMessage.NoSongs)

            action.favourite -> Message(MediaActionMessage.AddedToFavourites(songs.size))

            else -> Message(
                MediaActionMessage.RemovedFromFavourites(songs.size),
                SnackbarAction(SnackbarAction.Label.Undo, MediaAction.Favourite(MediaSelection.Songs(songs))),
            )
        }
    }

    private suspend fun createPlaylist(action: MediaAction.CreatePlaylist): MediaActionResult {
        val playlist = createPlaylist(action.name, action.selection)
        return Message(MediaActionMessage.PlaylistCreated(playlist.name))
    }

    private suspend fun goTo(selection: MediaSelection, destination: FindGoToTarget.Destination): MediaActionResult = findGoToTarget(selection, destination)?.let { MediaActionResult.Navigate(it) } ?: Message(MediaActionMessage.NotFound)

    private suspend fun editTags(action: MediaAction.EditTags): MediaActionResult {
        val songs = resolveSongs(action.selection).filter { it.mediaProvider.supportsTagEditing }
        if (songs.isEmpty()) return Message(MediaActionMessage.NoSongs)
        return MediaActionResult.Navigate(NavigationTarget.TagEditor(songs))
    }

    private suspend fun songInfo(action: MediaAction.SongInfo): MediaActionResult {
        val song = resolveSongs(action.selection).firstOrNull() ?: return Message(MediaActionMessage.NoSongs)
        return MediaActionResult.Navigate(NavigationTarget.SongInfo(song))
    }

    private suspend fun exclude(action: MediaAction.Exclude): MediaActionResult {
        val songs = excludeSongs(action.selection)
        if (songs.isEmpty()) return Message(MediaActionMessage.NoSongs)
        // The Undo names the songs themselves: an excluded album or artist no longer resolves to them.
        return Message(
            MediaActionMessage.Excluded(songs.size),
            SnackbarAction(SnackbarAction.Label.Undo, MediaAction.Include(MediaSelection.Songs(songs))),
        )
    }

    private suspend fun delete(action: MediaAction.Delete): MediaActionResult {
        if (!action.confirmed) {
            val songs = resolveSongs(action.selection)
            if (songs.isEmpty()) return Message(MediaActionMessage.NoSongs)
            return MediaActionResult.ConfirmationRequired(
                MediaActionMessage.ConfirmDelete(songs.singleOrNull()?.name, songs.size),
                // Confirm the songs that were shown, not whatever the selection resolves to later.
                MediaAction.Delete(MediaSelection.Songs(songs), confirmed = true),
            )
        }
        val result = deleteSongs(action.selection)
        return when {
            result.failed.isNotEmpty() -> Message(MediaActionMessage.DeleteFailed(result.failed.size))
            result.deleted.isEmpty() -> Message(MediaActionMessage.NoSongs)
            else -> Message(MediaActionMessage.Deleted(result.deleted.size))
        }
    }

    private suspend fun download(selection: MediaSelection): MediaActionResult {
        val result = downloadSongs(selection, download = true)
        return when {
            // The gate has already asked for the paywall
            result.needsPro -> MediaActionResult.None

            result.failed.isNotEmpty() -> Message(MediaActionMessage.DownloadFailed(result.failed.size))

            result.changed.isEmpty() -> Message(MediaActionMessage.NoSongs)

            else -> Message(MediaActionMessage.DownloadQueued(result.changed.size))
        }
    }

    private suspend fun removeDownload(selection: MediaSelection): MediaActionResult {
        val result = downloadSongs(selection, download = false)
        return Message(
            if (result.changed.isEmpty()) MediaActionMessage.NoSongs else MediaActionMessage.DownloadRemoved(result.changed.size),
        )
    }
}
