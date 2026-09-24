package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.shuttle.model.Song
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ReplayGainTags(val track: Double?, val album: Double?)

fun interface MediaStoreReplayGainReader {
    suspend fun read(
        uri: Uri,
        fileName: String,
        lastModified: Long,
        size: Long,
        mimeType: String?
    ): ReplayGainTags?
}

class KTagLibMediaStoreReplayGainReader(
    private val context: Context,
    private val kTagLib: KTagLib
) : MediaStoreReplayGainReader {
    override suspend fun read(
        uri: Uri,
        fileName: String,
        lastModified: Long,
        size: Long,
        mimeType: String?
    ): ReplayGainTags? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val audioFile = kTagLib.getAudioFile(pfd.detachFd(), uri.toString(), fileName, lastModified, size, mimeType)
                ReplayGainTags(audioFile.replayGainTrack, audioFile.replayGainAlbum)
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "Failed to read ReplayGain tags for uri: $uri")
            null
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to read ReplayGain tags for uri: $uri")
            null
        }
    }
}

internal suspend fun Song.withReplayGainTags(reader: MediaStoreReplayGainReader): Song {
    val id = externalId?.toLongOrNull() ?: return this
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val tags =
        reader.read(
            uri = uri,
            fileName = path.substringAfterLast('/'),
            lastModified = lastModified?.toEpochMilliseconds() ?: 0L,
            size = size,
            mimeType = mimeType
        )
    return tags?.let { copy(replayGainTrack = it.track, replayGainAlbum = it.album) } ?: this
}
