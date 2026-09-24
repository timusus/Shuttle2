package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ReplayGainTags(val track: Double?, val album: Double?)

/**
 * Reads a file's ReplayGain tags. May throw if the file can't be opened or parsed; callers treat that as "no tags".
 */
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
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val audioFile = kTagLib.getAudioFile(pfd.detachFd(), uri.toString(), fileName, lastModified, size, mimeType)
            ReplayGainTags(audioFile.replayGainTrack, audioFile.replayGainAlbum)
        }
    }
}

/**
 * Adds ReplayGain tags to songs built from the MediaStore cursor.
 *
 * Opening a file and parsing its tags costs far more than reading the cursor, so a song whose file is unchanged since the
 * last import (an existing song with the same path, modified date and size) keeps its stored values instead of being
 * read again. [readUnchanged] forces a read of every file, for the one-off backfill of songs imported before ReplayGain
 * was read for MediaStore at all. A file that can't be read gets null values rather than failing the import.
 */
internal fun List<Song>.withReplayGainTags(
    existingSongs: List<Song>,
    reader: MediaStoreReplayGainReader,
    readUnchanged: Boolean,
    concurrency: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
): Flow<Song> {
    val existingSongsByPath = existingSongs.associateBy { it.path }
    return asFlow().concurrentMap(concurrency) { song ->
        val existingSong = existingSongsByPath[song.path]
        if (!readUnchanged && existingSong != null && existingSong.lastModified == song.lastModified && existingSong.size == song.size) {
            song.copy(replayGainTrack = existingSong.replayGainTrack, replayGainAlbum = existingSong.replayGainAlbum)
        } else {
            song.withReplayGainTags(reader)
        }
    }
}

internal suspend fun Song.withReplayGainTags(reader: MediaStoreReplayGainReader): Song {
    val id = externalId?.toLongOrNull() ?: return this
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val tags =
        try {
            reader.read(
                uri = uri,
                fileName = path.substringAfterLast('/'),
                lastModified = lastModified?.toEpochMilliseconds() ?: 0L,
                size = size,
                mimeType = mimeType
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The native tag parse can throw anything for a corrupt file; one bad file shouldn't fail the whole import
            Timber.w("Failed to read ReplayGain tags for uri: $uri (${e.javaClass.simpleName}: ${e.message})")
            null
        }
    return tags?.let { copy(replayGainTrack = it.track, replayGainAlbum = it.album) } ?: this
}
