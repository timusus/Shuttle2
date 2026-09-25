package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song

/** The kinds of action the shared actions sheet offers; [AvailableMediaActions] lists the ones a selection allows. */
enum class MediaActionType {
    Play,
    Shuffle,
    PlayNext,
    AddToQueue,

    /** Opens the playlist picker, which then sends [MediaAction.AddToPlaylist] or [MediaAction.CreatePlaylist]. */
    AddToPlaylist,
    GoToAlbum,
    GoToArtist,
    EditTags,
    SongInfo,
    Share,
    Exclude,
    Delete,
    Download,
    RemoveDownload,
    ;

    /** The action this entry sends when tapped; null for [AddToPlaylist], which needs a playlist picked first. */
    fun actionFor(selection: MediaSelection): MediaAction? = when (this) {
        Play -> MediaAction.Play(selection)
        Shuffle -> MediaAction.Shuffle(selection)
        PlayNext -> MediaAction.PlayNext(selection)
        AddToQueue -> MediaAction.AddToQueue(selection)
        AddToPlaylist -> null
        GoToAlbum -> MediaAction.GoToAlbum(selection)
        GoToArtist -> MediaAction.GoToArtist(selection)
        EditTags -> MediaAction.EditTags(selection)
        SongInfo -> MediaAction.SongInfo(selection)
        Share -> MediaAction.Share(selection)
        Exclude -> MediaAction.Exclude(selection)
        Delete -> MediaAction.Delete(selection)
        Download -> MediaAction.Download(selection)
        RemoveDownload -> MediaAction.RemoveDownload(selection)
    }
}

/** Something the user asked to do with a [MediaSelection]. [MediaActionHandler] carries it out. */
sealed interface MediaAction {
    val selection: MediaSelection

    /** Replaces the queue with the selection's songs and plays from [position]. */
    data class Play(override val selection: MediaSelection, val position: Int = 0) : MediaAction

    data class Shuffle(override val selection: MediaSelection) : MediaAction

    data class PlayNext(override val selection: MediaSelection) : MediaAction

    data class AddToQueue(override val selection: MediaSelection) : MediaAction

    /** [ignoreDuplicates] is the snackbar's "Add anyway". */
    data class AddToPlaylist(
        override val selection: MediaSelection,
        val playlist: Playlist,
        val ignoreDuplicates: Boolean = false,
    ) : MediaAction

    /** The picker's "New playlist": creates [name] holding the selection's songs. */
    data class CreatePlaylist(override val selection: MediaSelection, val name: String) : MediaAction

    data class GoToAlbum(override val selection: MediaSelection) : MediaAction

    data class GoToArtist(override val selection: MediaSelection) : MediaAction

    data class EditTags(override val selection: MediaSelection) : MediaAction

    data class SongInfo(override val selection: MediaSelection) : MediaAction

    data class Share(override val selection: MediaSelection) : MediaAction

    /** Hides the selection's songs straight away; the result's Undo sends [Include]. */
    data class Exclude(override val selection: MediaSelection) : MediaAction

    /** Brings excluded songs back: Exclude's Undo. */
    data class Include(override val selection: MediaSelection) : MediaAction

    /** Takes [entries] out of [playlist]; the result's Undo is a [RestoreToPlaylist]. */
    data class RemoveFromPlaylist(val playlist: Playlist, val entries: List<PlaylistSong>, val before: List<PlaylistSong>) : MediaAction {
        override val selection: MediaSelection get() = MediaSelection.Songs(entries.map { it.song })
    }

    /** Puts [entries] back into [playlist] in the order [before] had. */
    data class RestoreToPlaylist(val playlist: Playlist, val entries: List<PlaylistSong>, val before: List<PlaylistSong>) : MediaAction {
        override val selection: MediaSelection get() = MediaSelection.Songs(entries.map { it.song })
    }

    /** Deletes the song files. Unconfirmed, it asks for a confirmation that sends it again with [confirmed] true. */
    data class Delete(override val selection: MediaSelection, val confirmed: Boolean = false) : MediaAction

    data class Download(override val selection: MediaSelection) : MediaAction

    data class RemoveDownload(override val selection: MediaSelection) : MediaAction
}

/** What the UI should do once a [MediaAction] has run. */
sealed interface MediaActionResult {
    /** Done; nothing to show. */
    data object None : MediaActionResult

    /** Show a snackbar with [message], and [action] as its button if there is one. */
    data class Message(val message: MediaActionMessage, val action: SnackbarAction? = null) : MediaActionResult

    data class Navigate(val target: NavigationTarget) : MediaActionResult

    /** Ask the user to confirm [message]; on yes, send [confirmAction]. */
    data class ConfirmationRequired(val message: MediaActionMessage, val confirmAction: MediaAction) : MediaActionResult

    /** Open the share sheet with `Intent.createChooser(request.toIntent(), null)`. */
    data class Share(val request: ShareRequest) : MediaActionResult
}

/** A snackbar button, and the action it sends. */
data class SnackbarAction(val label: Label, val action: MediaAction) {
    enum class Label { Undo, AddAnyway }
}

/** A screen a [MediaAction] opens. */
sealed interface NavigationTarget {
    data class Album(val album: com.simplecityapps.shuttle.model.Album) : NavigationTarget

    data class AlbumArtist(val albumArtist: com.simplecityapps.shuttle.model.AlbumArtist) : NavigationTarget

    /** The tag editor, for the selection's songs whose provider supports tag editing. */
    data class TagEditor(val songs: List<Song>) : NavigationTarget

    data class SongInfo(val song: Song) : NavigationTarget
}

/** The text of a snackbar or confirmation, as data; [format] turns it into a string. */
sealed interface MediaActionMessage {
    data object NoSongs : MediaActionMessage

    data class PlaybackFailed(val reason: String?) : MediaActionMessage

    data class AddedToQueue(val songCount: Int) : MediaActionMessage

    data class AddedToPlaylist(val playlistName: String, val songCount: Int) : MediaActionMessage

    /** "2 already in playlist", offered with "Add anyway". */
    data class AlreadyInPlaylist(val playlistName: String, val duplicateCount: Int) : MediaActionMessage

    data class AddToPlaylistFailed(val reason: String?) : MediaActionMessage

    data class PlaylistCreated(val playlistName: String) : MediaActionMessage

    data class Excluded(val songCount: Int) : MediaActionMessage
    data class RemovedFromPlaylist(val playlistName: String, val songCount: Int) : MediaActionMessage

    /** The Delete confirmation: [itemName] for a single song, else [songCount]. */
    data class ConfirmDelete(val itemName: String?, val songCount: Int) : MediaActionMessage

    data class Deleted(val songCount: Int) : MediaActionMessage

    data class DeleteFailed(val songCount: Int) : MediaActionMessage

    data class DownloadQueued(val songCount: Int) : MediaActionMessage

    data class DownloadFailed(val songCount: Int) : MediaActionMessage

    data class DownloadRemoved(val songCount: Int) : MediaActionMessage

    /** Go to album/artist found nothing, e.g. the song's album is excluded. */
    data object NotFound : MediaActionMessage
}
