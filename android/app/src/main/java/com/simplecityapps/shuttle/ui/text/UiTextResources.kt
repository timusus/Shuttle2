package com.simplecityapps.shuttle.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import com.simplecityapps.core.R as CoreR
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
        StringKey.PREF_CATEGORY_TITLE_DISPLAY -> R.string.pref_category_title_display
        StringKey.SETTINGS_DESTINATION_PLAYBACK_AND_SOUND -> R.string.settings_destination_playback_and_sound
        StringKey.SETTINGS_DESTINATION_SOURCES -> R.string.settings_destination_sources
        StringKey.SETTINGS_DESTINATION_LIBRARY -> R.string.settings_destination_library
        StringKey.PREF_CATEGORY_TITLE_PRIVACY -> R.string.pref_category_title_privacy
        StringKey.SETTINGS_DESTINATION_ABOUT -> R.string.settings_destination_about
        StringKey.PREF_STREAMING_QUALITY_ORIGINAL -> R.string.pref_streaming_quality_original
        StringKey.PREF_STREAMING_QUALITY_320 -> R.string.pref_streaming_quality_320
        StringKey.PREF_STREAMING_QUALITY_192 -> R.string.pref_streaming_quality_192
        StringKey.PREF_STREAMING_QUALITY_128 -> R.string.pref_streaming_quality_128
        StringKey.PREF_THEME_TITLE -> R.string.pref_theme_title
        StringKey.THEME_ENTRY_DAY_NIGHT -> R.string.theme_entry_day_night
        StringKey.THEME_ENTRY_LIGHT -> R.string.theme_entry_light
        StringKey.THEME_ENTRY_DARK -> R.string.theme_entry_dark
        StringKey.PREF_DYNAMIC_COLOUR_TITLE -> R.string.pref_dynamic_colour_title
        StringKey.PREF_DYNAMIC_COLOUR_SUMMARY -> R.string.pref_dynamic_colour_summary
        StringKey.PREF_THEME_ACCENT_TITLE -> R.string.pref_theme_accent_title
        StringKey.THEME_ACCENT_ENTRY_NEUTRAL -> R.string.theme_accent_entry_neutral
        StringKey.THEME_ACCENT_ENTRY_BLUE -> R.string.theme_accent_entry_blue
        StringKey.THEME_ACCENT_ENTRY_ORANGE -> R.string.theme_accent_entry_orange
        StringKey.THEME_ACCENT_ENTRY_CYAN -> R.string.theme_accent_entry_cyan
        StringKey.THEME_ACCENT_ENTRY_PURPLE -> R.string.theme_accent_entry_purple
        StringKey.THEME_ACCENT_ENTRY_GREEN -> R.string.theme_accent_entry_green
        StringKey.THEME_ACCENT_ENTRY_AMBER -> R.string.theme_accent_entry_amber
        StringKey.PREF_THEME_ACCENT_DYNAMIC_COLOUR_HINT -> R.string.pref_theme_accent_dynamic_colour_hint
        StringKey.PREF_COLOUR_FROM_ARTWORK_TITLE -> R.string.pref_colour_from_artwork_title
        StringKey.PREF_COLOUR_FROM_ARTWORK_SUMMARY -> R.string.pref_colour_from_artwork_summary
        StringKey.PREF_PURE_BLACK_TITLE -> R.string.pref_pure_black_title
        StringKey.PREF_PURE_BLACK_SUMMARY -> R.string.pref_pure_black_summary
        StringKey.PREF_CATEGORY_TITLE_WIDGETS -> R.string.pref_category_title_widgets
        StringKey.PREF_WIDGET_OPACITY_TITLE -> R.string.pref_widget_opacity_title
        StringKey.PREF_NAVIGATION_TITLE -> R.string.pref_navigation_title
        StringKey.PREF_SHOW_HOME_ON_LAUNCH_TITLE -> R.string.pref_show_home_on_launch_title
        StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_TITLE -> R.string.pref_disable_shuffle_on_queue_title
        StringKey.PREF_DISABLE_SHUFFLE_ON_QUEUE_SUBTITLE -> R.string.pref_disable_shuffle_on_queue_subtitle
        StringKey.PREF_BIT_PERFECT_USB_TITLE -> R.string.pref_bit_perfect_usb_title
        StringKey.PREF_BIT_PERFECT_USB_SUBTITLE -> R.string.pref_bit_perfect_usb_subtitle
        StringKey.DSP_EQUALIZER_TITLE -> R.string.dsp_equalizer_title
        StringKey.DSP_REPLAY_GAIN_TITLE -> R.string.dsp_replay_gain_title
        StringKey.DSP_REPLAY_GAIN_TRACK -> R.string.dsp_replay_gain_track
        StringKey.DSP_REPLAY_GAIN_ALBUM -> R.string.dsp_replay_gain_album
        StringKey.DSP_REPLAY_GAIN_OFF -> R.string.dsp_replay_gain_off
        StringKey.DSP_PREAMP -> R.string.dsp_preamp
        StringKey.PREF_REPORT_PLAYBACK_TITLE -> R.string.pref_report_playback_title
        StringKey.PREF_REPORT_PLAYBACK_SUMMARY -> R.string.pref_report_playback_summary
        StringKey.PREF_DOWNLOAD_WIFI_ONLY_TITLE -> R.string.pref_download_wifi_only_title
        StringKey.PREF_DOWNLOAD_WIFI_ONLY_SUMMARY -> R.string.pref_download_wifi_only_summary
        StringKey.PREF_CATEGORY_TITLE_STREAMING_QUALITY -> R.string.pref_category_title_streaming_quality
        StringKey.PREF_STREAMING_QUALITY_UNMETERED_TITLE -> R.string.pref_streaming_quality_unmetered_title
        StringKey.PREF_STREAMING_QUALITY_METERED_TITLE -> R.string.pref_streaming_quality_metered_title
        StringKey.PREF_MEDIA_RESCAN_TITLE -> R.string.pref_media_rescan_title
        StringKey.PREF_MEDIA_RESCAN_SUMMARY -> R.string.pref_media_rescan_summary
        StringKey.PREF_BACKUP_EXPORT_TITLE -> R.string.pref_backup_export_title
        StringKey.PREF_BACKUP_EXPORT_SUMMARY -> R.string.pref_backup_export_summary
        StringKey.PREF_BACKUP_IMPORT_TITLE -> R.string.pref_backup_import_title
        StringKey.PREF_BACKUP_IMPORT_SUMMARY -> R.string.pref_backup_import_summary
        StringKey.PREF_RESCAN_FREQUENCY_TITLE -> R.string.pref_rescan_frequency_title
        StringKey.PREF_RESCAN_FREQUENCY_NEVER -> R.string.pref_rescan_frequency_never
        StringKey.PREF_RESCAN_FREQUENCY_DAILY -> R.string.pref_rescan_frequency_daily
        StringKey.PREF_RESCAN_FREQUENCY_WEEKLY -> R.string.pref_rescan_frequency_weekly
        StringKey.PREF_EXCLUDE_TITLE -> R.string.pref_exclude_title
        StringKey.PREF_EXCLUDE_SUMMARY -> R.string.pref_exclude_summary
        StringKey.PREF_CATEGORY_TITLE_ARTWORK -> R.string.pref_category_title_artwork
        StringKey.PREF_CATEGORY_TITLE_BACKUP -> R.string.pref_category_title_backup
        StringKey.PREF_ARTWORK_WIFI_TITLE -> R.string.pref_artwork_wifi_title
        StringKey.PREF_ARTWORK_WIFI_SUBTITLE -> R.string.pref_artwork_wifi_subtitle
        StringKey.PREF_ARTWORK_LOCAL_ONLY_TITLE -> R.string.pref_artwork_local_only_title
        StringKey.PREF_ARTWORK_LOCAL_ONLY_SUBTITLE -> R.string.pref_artwork_local_only_subtitle
        StringKey.PREF_MEDIA_SESSION_ARTWORK_TITLE -> R.string.pref_media_session_artwork_title
        StringKey.PREF_MEDIA_SESSION_ARTWORK_SUBTITLE -> R.string.pref_media_session_artwork_subtitle
        StringKey.PREF_CLEAR_ARTWORK_TITLE -> R.string.pref_clear_artwork_title
        StringKey.PREF_CLEAR_ARTWORK_SUBTITLE -> R.string.pref_clear_artwork_subtitle
        StringKey.SETTINGS_DIALOG_TITLE_CLEAR_ARTWORK -> R.string.settings_dialog_title_clear_artwork
        StringKey.SETTINGS_DIALOG_MESSAGE_CLEAR_ARTWORK -> R.string.settings_dialog_message_clear_artwork
        StringKey.SETTINGS_DIALOG_BUTTON_CLEAR_ARTWORK -> R.string.settings_dialog_button_clear_artwork
        StringKey.PREF_DOWNLOAD_ARTWORK_TITLE -> R.string.pref_download_artwork_title
        StringKey.SETTINGS_DIALOG_TITLE_DOWNLOAD_ARTWORK -> R.string.settings_dialog_title_download_artwork
        StringKey.SETTINGS_DIALOG_MESSAGE_DOWNLOAD_ARTWORK -> R.string.settings_dialog_message_download_artwork
        StringKey.SETTINGS_DIALOG_BUTTON_DOWNLOAD_ARTWORK -> R.string.settings_dialog_button_download_artwork
        StringKey.PREF_CRASH_REPORTING_TITLE -> R.string.pref_crash_reporting_title
        StringKey.PREF_CRASH_REPORTING_SUBTITLE -> R.string.pref_crash_reporting_subtitle
        StringKey.PREF_ANALYTICS_TITLE -> R.string.pref_analytics_title
        StringKey.PREF_ANALYTICS_SUBTITLE -> R.string.pref_analytics_subtitle
        StringKey.PREF_VIEW_CHANGELOG_TITLE -> R.string.pref_view_changelog_title
        StringKey.PREF_VIEW_LICENSES_TITLE -> R.string.pref_view_licenses_title
        StringKey.SETTINGS_GROUP_ADVANCED -> R.string.settings_group_advanced
        StringKey.PREF_FILE_LOGGING_TITLE -> R.string.pref_file_logging_title
        StringKey.PREF_FILE_LOGGING_SUBTITLE -> R.string.pref_file_logging_subtitle
        StringKey.PREF_COPY_DEBUG_LOGS_SUBTITLE -> R.string.pref_copy_debug_logs_subtitle
        StringKey.PREF_VIEW_LIVE_LOG_TITLE -> R.string.pref_view_live_log_title
        StringKey.EDIT_TAGS_HINT_TITLE -> R.string.edit_tags_hint_title
        StringKey.EDIT_TAGS_HINT_ARTIST -> R.string.edit_tags_hint_artist
        StringKey.EDIT_TAGS_HINT_ALBUM -> R.string.edit_tags_hint_album
        StringKey.EDIT_TAGS_HINT_ALBUM_ARTIST -> R.string.edit_tags_hint_album_artist
        StringKey.EDIT_TAGS_HINT_YEAR -> R.string.edit_tags_hint_year
        StringKey.EDIT_TAGS_HINT_TRACK -> R.string.edit_tags_hint_track
        StringKey.EDIT_TAGS_HINT_TRACK_TOTAL -> R.string.edit_tags_hint_track_total
        StringKey.EDIT_TAGS_HINT_DISC -> R.string.edit_tags_hint_disc
        StringKey.EDIT_TAGS_HINT_DISC_TOTAL -> R.string.edit_tags_hint_disc_total
        StringKey.EDIT_TAGS_HINT_GENRES -> R.string.edit_tags_hint_genres
        StringKey.EDIT_TAGS_HINT_LYRICS -> R.string.edit_tags_hint_lyrics
        StringKey.EDIT_TAGS_SECTION_SONG -> R.string.edit_tags_section_song
        StringKey.EDIT_TAGS_SECTION_ALBUM -> R.string.edit_tags_section_album
        StringKey.EDIT_TAGS_SECTION_NUMBERING -> R.string.edit_tags_section_numbering
        StringKey.EDIT_TAGS_SECTION_LYRICS -> R.string.edit_tags_section_lyrics
        StringKey.EQ_PRESET_FLAT -> CoreR.string.eq_preset_flat
        StringKey.EQ_PRESET_CUSTOM -> CoreR.string.eq_preset_custom
        StringKey.EQ_PRESET_BASS_BOOST -> CoreR.string.eq_preset_bass_boost
        StringKey.EQ_PRESET_BASS_REDUCE -> CoreR.string.eq_preset_bass_reduce
        StringKey.EQ_PRESET_VOCAL_BOOST -> CoreR.string.eq_preset_vocal_boost
        StringKey.EQ_PRESET_VOCAL_REDUCE -> CoreR.string.eq_preset_vocal_reduce
        StringKey.HOME_JUMP_BACK_IN_SUBTITLE -> R.string.home_jump_back_in_subtitle
        StringKey.HOME_AROUND_THIS_TIME_SUBTITLE -> R.string.home_around_this_time_subtitle
        StringKey.HOME_HEAVY_ROTATION_SUBTITLE -> R.string.home_heavy_rotation_subtitle
        StringKey.HOME_REDISCOVER_SUBTITLE -> R.string.home_rediscover_subtitle
        StringKey.HOME_RECENTLY_ADDED_SUBTITLE -> R.string.home_recently_added_subtitle
        StringKey.HOME_GENRE_PICKS_SUBTITLE -> R.string.home_genre_picks_subtitle
        StringKey.HOME_GENRE_PICKS_LARGEST_SUBTITLE -> R.string.home_genre_picks_largest_subtitle
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
