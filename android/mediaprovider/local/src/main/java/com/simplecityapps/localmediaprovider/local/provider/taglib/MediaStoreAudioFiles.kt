package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull

/**
 * An audio file MediaStore has indexed. The TagLib scanner finds files this way and reads their tags through [contentUri],
 * so it needs only the audio permission, not a folder grant.
 */
data class MediaStoreAudioFile(
    val id: Long,
    val path: String,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
    // Milliseconds, as MediaStore read it; null if MediaStore couldn't
    val duration: Long? = null
) {
    // The "external" volume spans every mounted volume (SD cards included), and ids are unique across them
    val contentUri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

internal val MEDIA_STORE_AUDIO_PROJECTION =
    arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DURATION
    )

// The same rows the MediaStore provider imports: leaves out ringtones, alarms, notifications and recordings
internal const val MEDIA_STORE_AUDIO_SELECTION = "${MediaStore.Audio.Media.IS_MUSIC}=1 OR ${MediaStore.Audio.Media.IS_PODCAST}=1"

/**
 * Reads the audio files in a cursor over [MEDIA_STORE_AUDIO_PROJECTION] that [folderFilter] accepts. A row with no path
 * can't be filtered or matched to a song, so it's skipped.
 */
internal fun Cursor.readMediaStoreAudioFiles(folderFilter: FolderFilter): List<MediaStoreAudioFile> {
    val idColumn = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
    val pathColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
    val displayNameColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
    val sizeColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
    val dateModifiedColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
    val mimeTypeColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
    val durationColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    val files = mutableListOf<MediaStoreAudioFile>()
    while (moveToNext()) {
        val path = getStringOrNull(pathColumn) ?: continue
        if (!folderFilter.accepts(path)) continue
        files +=
            MediaStoreAudioFile(
                id = getLong(idColumn),
                path = path,
                displayName = getStringOrNull(displayNameColumn) ?: path.substringAfterLast('/'),
                size = getLong(sizeColumn),
                // MediaStore stores seconds
                lastModified = getLong(dateModifiedColumn) * 1000,
                mimeType = getStringOrNull(mimeTypeColumn),
                duration = getLongOrNull(durationColumn)
            )
    }
    return files
}
