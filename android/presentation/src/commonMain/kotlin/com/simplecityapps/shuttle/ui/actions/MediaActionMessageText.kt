package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.ui.text.PluralKey
import com.simplecityapps.shuttle.ui.text.StringKey
import com.simplecityapps.shuttle.ui.text.UiText
import com.simplecityapps.shuttle.ui.text.text

/** The user-facing text of a [MediaActionMessage]; each platform resolves it where it shows the message. */
fun MediaActionMessage.text(): UiText = when (this) {
    is MediaActionMessage.NoSongs -> StringKey.MEDIA_ACTION_NO_SONGS.text

    is MediaActionMessage.PlaybackFailed -> UiText.Resource(StringKey.MEDIA_ACTION_PLAYBACK_FAILED, listOf(reason ?: StringKey.ERROR_UNKNOWN.text))

    is MediaActionMessage.AddedToQueue -> UiText.Plural(PluralKey.QUEUE_SONGS_ADDED, songCount)

    is MediaActionMessage.AddedToPlaylist -> UiText.Plural(PluralKey.PLAYLIST_SONGS_ADDED, songCount, listOf(playlistName))

    is MediaActionMessage.AlreadyInPlaylist -> UiText.Plural(PluralKey.MEDIA_ACTION_ALREADY_IN_PLAYLIST, duplicateCount, listOf(playlistName))

    is MediaActionMessage.AddToPlaylistFailed -> UiText.Resource(
        StringKey.PLAYLIST_MENU_CREATE_PLAYLIST_FAILURE,
        listOf(reason ?: StringKey.ERROR_UNKNOWN.text)
    )

    is MediaActionMessage.PlaylistCreated -> UiText.Resource(StringKey.PLAYLIST_MENU_CREATE_PLAYLIST_SUCCESS, listOf(playlistName))

    is MediaActionMessage.AddedToFavourites -> UiText.Plural(PluralKey.PLAYLIST_SONGS_ADDED, songCount, listOf(StringKey.PLAYLIST_TITLE_FAVORITES.text))

    is MediaActionMessage.RemovedFromFavourites -> UiText.Plural(
        PluralKey.MEDIA_ACTION_REMOVED_FROM_PLAYLIST,
        songCount,
        listOf(StringKey.PLAYLIST_TITLE_FAVORITES.text)
    )

    is MediaActionMessage.Excluded -> UiText.Plural(PluralKey.MEDIA_ACTION_EXCLUDED, songCount)

    is MediaActionMessage.RemovedFromPlaylist -> UiText.Plural(PluralKey.MEDIA_ACTION_REMOVED_FROM_PLAYLIST, songCount, listOf(playlistName))

    is MediaActionMessage.ConfirmDelete -> if (itemName != null && songCount == 1) {
        UiText.Resource(StringKey.DIALOG_DELETE_MESSAGE, listOf(itemName))
    } else {
        UiText.Plural(PluralKey.MEDIA_ACTION_CONFIRM_DELETE, songCount)
    }

    is MediaActionMessage.Deleted -> UiText.Plural(PluralKey.MEDIA_ACTION_DELETED, songCount)

    is MediaActionMessage.DeleteFailed -> UiText.Plural(PluralKey.MEDIA_ACTION_DELETE_FAILED, songCount)

    is MediaActionMessage.DownloadQueued -> UiText.Plural(PluralKey.MEDIA_ACTION_DOWNLOAD_QUEUED, songCount)

    is MediaActionMessage.DownloadFailed -> UiText.Plural(PluralKey.MEDIA_ACTION_DOWNLOAD_FAILED, songCount)

    is MediaActionMessage.DownloadRemoved -> UiText.Plural(PluralKey.MEDIA_ACTION_DOWNLOAD_REMOVED, songCount)

    is MediaActionMessage.NotFound -> StringKey.MEDIA_ACTION_NOT_FOUND.text
}

/** The snackbar button's text. */
fun SnackbarAction.Label.text(): UiText = when (this) {
    SnackbarAction.Label.Undo -> StringKey.MEDIA_ACTION_UNDO.text
    SnackbarAction.Label.AddAnyway -> StringKey.MEDIA_ACTION_ADD_ANYWAY.text
}
