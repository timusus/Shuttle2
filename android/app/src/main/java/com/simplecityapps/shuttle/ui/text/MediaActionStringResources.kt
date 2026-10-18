package com.simplecityapps.shuttle.ui.text

import androidx.annotation.StringRes
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.R

@get:StringRes
val MediaActionString.resId: Int
    get() = when (this) {
        MediaActionString.MEDIA_ACTION_NO_SONGS -> R.string.media_action_no_songs
        MediaActionString.MEDIA_ACTION_PLAYBACK_FAILED -> R.string.media_action_playback_failed
        MediaActionString.MEDIA_ACTION_NOT_FOUND -> R.string.media_action_not_found
        MediaActionString.MEDIA_ACTION_UNDO -> R.string.media_action_undo
        MediaActionString.MEDIA_ACTION_ADD_ANYWAY -> R.string.media_action_add_anyway
        MediaActionString.PLAYLIST_MENU_CREATE_PLAYLIST_SUCCESS -> R.string.playlist_menu_create_playlist_success
        MediaActionString.PLAYLIST_MENU_CREATE_PLAYLIST_FAILURE -> R.string.playlist_menu_create_playlist_failure
        MediaActionString.PLAYLIST_TITLE_FAVORITES -> MediaProviderR.string.playlist_title_favorites
        MediaActionString.PLAYLIST_TITLE_RECENTLY_ADDED -> MediaProviderR.string.playlist_title_recently_added
        MediaActionString.PLAYLIST_TITLE_MOST_PLAYED -> MediaProviderR.string.playlist_title_most_played
        MediaActionString.PLAYLIST_TITLE_HISTORY -> MediaProviderR.string.playlist_title_history
        MediaActionString.PLAYLIST_TITLE_DOWNLOADED -> MediaProviderR.string.playlist_title_downloaded
        MediaActionString.DIALOG_DELETE_MESSAGE -> R.string.dialog_delete_message
        MediaActionString.ERROR_UNKNOWN -> R.string.error_unknown
    }
