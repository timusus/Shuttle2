package com.simplecityapps.shuttle.ui.actions

import android.content.res.Resources
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.R

/** The user-facing text of a [MediaActionMessage]. */
fun MediaActionMessage.format(resources: Resources): String = when (this) {
    is MediaActionMessage.NoSongs -> resources.getString(R.string.media_action_no_songs)

    is MediaActionMessage.PlaybackFailed -> resources.getString(
        R.string.media_action_playback_failed,
        reason ?: resources.getString(R.string.error_unknown)
    )

    is MediaActionMessage.AddedToQueue -> resources.getQuantityString(R.plurals.queue_songs_added, songCount, songCount)

    is MediaActionMessage.AddedToPlaylist -> resources.getQuantityString(
        R.plurals.playlist_songs_added,
        songCount,
        songCount,
        playlistName
    )

    is MediaActionMessage.AlreadyInPlaylist -> resources.getQuantityString(
        R.plurals.media_action_already_in_playlist,
        duplicateCount,
        duplicateCount,
        playlistName
    )

    is MediaActionMessage.AddToPlaylistFailed -> resources.getString(
        R.string.playlist_menu_create_playlist_failure,
        reason ?: resources.getString(R.string.error_unknown)
    )

    is MediaActionMessage.PlaylistCreated -> resources.getString(R.string.playlist_menu_create_playlist_success, playlistName)

    is MediaActionMessage.AddedToFavourites -> resources.getQuantityString(
        R.plurals.playlist_songs_added,
        songCount,
        songCount,
        resources.getString(MediaProviderR.string.playlist_title_favorites)
    )

    is MediaActionMessage.RemovedFromFavourites -> resources.getQuantityString(
        R.plurals.media_action_removed_from_playlist,
        songCount,
        songCount,
        resources.getString(MediaProviderR.string.playlist_title_favorites)
    )

    is MediaActionMessage.Excluded -> resources.getQuantityString(R.plurals.media_action_excluded, songCount, songCount)

    is MediaActionMessage.RemovedFromPlaylist -> resources.getQuantityString(
        R.plurals.media_action_removed_from_playlist,
        songCount,
        songCount,
        playlistName
    )

    is MediaActionMessage.ConfirmDelete -> if (itemName != null && songCount == 1) {
        resources.getString(R.string.dialog_delete_message, itemName)
    } else {
        resources.getQuantityString(R.plurals.media_action_confirm_delete, songCount, songCount)
    }

    is MediaActionMessage.Deleted -> resources.getQuantityString(R.plurals.media_action_deleted, songCount, songCount)

    is MediaActionMessage.DeleteFailed -> resources.getQuantityString(R.plurals.media_action_delete_failed, songCount, songCount)

    is MediaActionMessage.DownloadQueued -> resources.getQuantityString(R.plurals.media_action_download_queued, songCount, songCount)

    is MediaActionMessage.DownloadFailed -> resources.getQuantityString(R.plurals.media_action_download_failed, songCount, songCount)

    is MediaActionMessage.DownloadRemoved -> resources.getQuantityString(R.plurals.media_action_download_removed, songCount, songCount)

    is MediaActionMessage.NotFound -> resources.getString(R.string.media_action_not_found)
}

/** The snackbar button's text. */
fun SnackbarAction.Label.format(resources: Resources): String = when (this) {
    SnackbarAction.Label.Undo -> resources.getString(R.string.media_action_undo)
    SnackbarAction.Label.AddAnyway -> resources.getString(R.string.media_action_add_anyway)
}
