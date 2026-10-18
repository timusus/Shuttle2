package com.simplecityapps.shuttle.ui.text

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
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

/** The Android resource behind [this] key: `R.string.<key>`; each feature enum maps its own entries. */
@get:StringRes
val StringKey.resId: Int
    get() = when (this) {
        is SongInfoString -> resId
        is MediaActionString -> resId
        is SettingsString -> resId
        is TagEditorString -> resId
        is EqualizerPresetString -> resId
        is HomeString -> resId
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
        PluralKey.WORK_MOVEMENTS -> R.plurals.work_movements
        PluralKey.WORK_PARTS -> R.plurals.work_parts
        PluralKey.WORK_SONGS -> R.plurals.work_songs
    }
