package com.simplecityapps.shuttle.ui.actions

import android.content.res.Resources
import com.simplecityapps.shuttle.R
import com.squareup.phrase.Phrase

/** The user-facing text of a [MediaActionMessage]. */
fun MediaActionMessage.format(resources: Resources): String = when (this) {
    is MediaActionMessage.NoSongs -> resources.getString(R.string.media_action_no_songs)

    is MediaActionMessage.PlaybackFailed -> Phrase.from(resources, R.string.media_action_playback_failed)
        .put("reason", reason ?: resources.getString(R.string.error_unknown))
        .format().toString()

    is MediaActionMessage.AddedToQueue -> plural(resources, R.plurals.queue_songs_added, songCount)

    is MediaActionMessage.AddedToPlaylist -> Phrase.fromPlural(resources, R.plurals.playlist_songs_added, songCount)
        .put("count", songCount)
        .put("playlist_name", playlistName)
        .format().toString()

    is MediaActionMessage.AlreadyInPlaylist -> Phrase.fromPlural(resources, R.plurals.media_action_already_in_playlist, duplicateCount)
        .put("count", duplicateCount)
        .put("playlist_name", playlistName)
        .format().toString()

    is MediaActionMessage.AddToPlaylistFailed -> Phrase.from(resources, R.string.playlist_menu_create_playlist_failure)
        .put("error_message", reason ?: resources.getString(R.string.error_unknown))
        .format().toString()

    is MediaActionMessage.PlaylistCreated -> Phrase.from(resources, R.string.playlist_menu_create_playlist_success)
        .put("playlist_name", playlistName)
        .format().toString()

    is MediaActionMessage.Excluded -> plural(resources, R.plurals.media_action_excluded, songCount)

    is MediaActionMessage.ConfirmDelete -> if (itemName != null && songCount == 1) {
        Phrase.from(resources, R.string.dialog_delete_message).put("item", itemName).format().toString()
    } else {
        plural(resources, R.plurals.media_action_confirm_delete, songCount)
    }

    is MediaActionMessage.Deleted -> plural(resources, R.plurals.media_action_deleted, songCount)

    is MediaActionMessage.DeleteFailed -> plural(resources, R.plurals.media_action_delete_failed, songCount)

    is MediaActionMessage.DownloadQueued -> plural(resources, R.plurals.media_action_download_queued, songCount)

    is MediaActionMessage.DownloadFailed -> plural(resources, R.plurals.media_action_download_failed, songCount)

    is MediaActionMessage.DownloadRemoved -> plural(resources, R.plurals.media_action_download_removed, songCount)

    is MediaActionMessage.NotFound -> resources.getString(R.string.media_action_not_found)
}

/** The snackbar button's text. */
fun SnackbarAction.Label.format(resources: Resources): String = when (this) {
    SnackbarAction.Label.Undo -> resources.getString(R.string.media_action_undo)
    SnackbarAction.Label.AddAnyway -> resources.getString(R.string.media_action_add_anyway)
}

/** Plurals whose only placeholder is `{count}`; Phrase rejects a key the pattern doesn't have, hence the check. */
private fun plural(resources: Resources, id: Int, count: Int): String {
    val pattern = resources.getQuantityText(id, count)
    return if (pattern.contains("{count}")) {
        Phrase.from(pattern).put("count", count).format().toString()
    } else {
        pattern.toString()
    }
}
