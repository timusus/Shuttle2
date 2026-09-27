package com.simplecityapps.shuttle.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.simplecityapps.mediaprovider.R as MediaProviderR
import com.simplecityapps.shuttle.R

/** [text] resolved against the app's `strings.xml`, its [UiText] arguments first. */
fun Resources.getString(text: UiText): String = when (text) {
    is UiText.Resource -> getString(text.key.resId, *text.args.resolved(this))
    is UiText.Plural -> getQuantityString(text.key.resId, text.count, text.count, *text.args.resolved(this))
}

/** [text] resolved in composition, like `stringResource`. */
@Composable
@ReadOnlyComposable
fun stringResource(text: UiText): String = LocalResources.current.getString(text)

/** [key]'s string in composition, like `stringResource`. */
@Composable
@ReadOnlyComposable
fun stringResource(key: StringKey): String = androidx.compose.ui.res.stringResource(key.resId)

private fun List<Any>.resolved(resources: Resources): Array<Any> = map { if (it is UiText) resources.getString(it) else it }.toTypedArray()

/** The Android resource behind [this] key: `R.string.<key>`. */
@get:StringRes
val StringKey.resId: Int
    get() = when (this) {
        StringKey.SONG_INFO_SECTION_TAGS -> R.string.song_info_section_tags
        StringKey.SONG_INFO_SECTION_FILE -> R.string.song_info_section_file
        StringKey.SONG_INFO_SECTION_PLAYBACK -> R.string.song_info_section_playback
        StringKey.SONG_INFO_TRACK_TITLE -> R.string.song_info_track_title
        StringKey.SONG_INFO_ARTISTS -> R.string.song_info_artists
        StringKey.SONG_INFO_ALBUM -> R.string.song_info_album
        StringKey.SONG_INFO_ALBUM_ARTIST -> R.string.song_info_album_artist
        StringKey.SONG_INFO_YEAR -> R.string.song_info_year
        StringKey.SONG_INFO_TRACK_NUMBER -> R.string.song_info_track_number
        StringKey.SONG_INFO_DISC -> R.string.song_info_disc
        StringKey.SONG_INFO_GENRES -> R.string.song_info_genres
        StringKey.SONG_INFO_LYRICS -> R.string.song_info_lyrics
        StringKey.SONG_INFO_PATH -> R.string.song_info_path
        StringKey.SONG_INFO_MIME_TYPE -> R.string.song_info_mime_type
        StringKey.SONG_INFO_SIZE -> R.string.song_info_size
        StringKey.SONG_INFO_DURATION -> R.string.song_info_duration
        StringKey.SONG_INFO_BIT_RATE -> R.string.song_info_bit_rate
        StringKey.SONG_INFO_BIT_DEPTH -> R.string.song_info_bit_depth
        StringKey.SONG_INFO_SAMPLE_RATE -> R.string.song_info_sample_rate
        StringKey.SONG_INFO_CHANNEL_COUNT -> R.string.song_info_channel_count
        StringKey.SONG_INFO_PLAY_COUNT -> R.string.song_info_play_count
        StringKey.SONG_INFO_REPLAY_GAIN_TRACK -> R.string.song_info_replay_gain_track
        StringKey.SONG_INFO_REPLAY_GAIN_ALBUM -> R.string.song_info_replay_gain_album
        StringKey.MEDIA_ACTION_NO_SONGS -> R.string.media_action_no_songs
        StringKey.MEDIA_ACTION_PLAYBACK_FAILED -> R.string.media_action_playback_failed
        StringKey.MEDIA_ACTION_NOT_FOUND -> R.string.media_action_not_found
        StringKey.MEDIA_ACTION_UNDO -> R.string.media_action_undo
        StringKey.MEDIA_ACTION_ADD_ANYWAY -> R.string.media_action_add_anyway
        StringKey.PLAYLIST_MENU_CREATE_PLAYLIST_SUCCESS -> R.string.playlist_menu_create_playlist_success
        StringKey.PLAYLIST_MENU_CREATE_PLAYLIST_FAILURE -> R.string.playlist_menu_create_playlist_failure
        StringKey.PLAYLIST_TITLE_FAVORITES -> MediaProviderR.string.playlist_title_favorites
        StringKey.PLAYLIST_TITLE_RECENTLY_ADDED -> MediaProviderR.string.playlist_title_recently_added
        StringKey.PLAYLIST_TITLE_MOST_PLAYED -> MediaProviderR.string.playlist_title_most_played
        StringKey.PLAYLIST_TITLE_HISTORY -> MediaProviderR.string.playlist_title_history
        StringKey.DIALOG_DELETE_MESSAGE -> R.string.dialog_delete_message
        StringKey.ERROR_UNKNOWN -> R.string.error_unknown
    }

/** The Android resource behind [this] key: `R.plurals.<key>`. */
@get:PluralsRes
val PluralKey.resId: Int
    get() = when (this) {
        PluralKey.QUEUE_SONGS_ADDED -> R.plurals.queue_songs_added
        PluralKey.PLAYLIST_SONGS_ADDED -> R.plurals.playlist_songs_added
        PluralKey.MEDIA_ACTION_ALREADY_IN_PLAYLIST -> R.plurals.media_action_already_in_playlist
        PluralKey.MEDIA_ACTION_REMOVED_FROM_PLAYLIST -> R.plurals.media_action_removed_from_playlist
        PluralKey.MEDIA_ACTION_EXCLUDED -> R.plurals.media_action_excluded
        PluralKey.MEDIA_ACTION_CONFIRM_DELETE -> R.plurals.media_action_confirm_delete
        PluralKey.MEDIA_ACTION_DELETED -> R.plurals.media_action_deleted
        PluralKey.MEDIA_ACTION_DELETE_FAILED -> R.plurals.media_action_delete_failed
        PluralKey.MEDIA_ACTION_DOWNLOAD_QUEUED -> R.plurals.media_action_download_queued
        PluralKey.MEDIA_ACTION_DOWNLOAD_FAILED -> R.plurals.media_action_download_failed
        PluralKey.MEDIA_ACTION_DOWNLOAD_REMOVED -> R.plurals.media_action_download_removed
    }
