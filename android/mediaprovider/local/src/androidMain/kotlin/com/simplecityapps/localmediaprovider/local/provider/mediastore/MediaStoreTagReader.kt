package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FileTags
import com.simplecityapps.localmediaprovider.local.provider.toFileTags
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import timber.log.Timber

/**
 * Reads a file's tags. Returns null if TagLib can't parse the file, and may throw if it can't be opened; callers treat
 * both as "no tags".
 */
fun interface MediaStoreTagReader {
    suspend fun read(
        uri: Uri,
        fileName: String
    ): FileTags?
}

class KTagLibMediaStoreTagReader(
    private val context: Context,
    private val kTagLib: KTagLib
) : MediaStoreTagReader {
    override suspend fun read(
        uri: Uri,
        fileName: String
    ): FileTags? = withContext(Dispatchers.IO) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            kTagLib.getMetadata(pfd.detachFd(), fileName)?.propertyMap?.toFileTags()
        }
    }
}

/**
 * Adds the tags read from each file to songs built from the MediaStore cursor.
 *
 * MediaStore's own tag columns can't be trusted: its scanner doesn't read Matroska tags at all (a .mka file gets its file
 * name as title and its folder as album), and it mis-decodes some UTF-8 tags. So the values TagLib reads from the file win,
 * and MediaStore's stay only for fields the file doesn't tag, or for a file TagLib can't read.
 *
 * Opening a file and parsing its tags costs far more than reading the cursor, so a song whose file is unchanged since the
 * last import (an existing song with the same path, modified date and size) keeps its stored values instead of being
 * read again. [readUnchanged] forces a read of every file, for the one-off backfill of songs imported before their tags
 * were read from the file. A file that can't be read keeps MediaStore's values rather than failing the import.
 */
internal fun List<Song>.withFileTags(
    existingSongs: List<Song>,
    reader: MediaStoreTagReader,
    readUnchanged: Boolean,
    concurrency: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)
): Flow<Song> {
    val existingSongsByPath = existingSongs.associateBy { it.path }
    return asFlow().concurrentMap(concurrency) { song ->
        val existingSong = existingSongsByPath[song.path]
        if (!readUnchanged && existingSong != null && existingSong.lastModified == song.lastModified && existingSong.size == song.size) {
            song.copy(
                name = existingSong.name,
                artists = existingSong.artists,
                albumArtist = existingSong.albumArtist,
                album = existingSong.album,
                track = existingSong.track,
                disc = existingSong.disc,
                date = existingSong.date,
                replayGainTrack = existingSong.replayGainTrack,
                replayGainAlbum = existingSong.replayGainAlbum
            )
        } else {
            song.withFileTags(reader)
        }
    }
}

internal suspend fun Song.withFileTags(reader: MediaStoreTagReader): Song {
    val id = externalId?.toLongOrNull() ?: return this
    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val tags =
        try {
            reader.read(uri = uri, fileName = path.substringAfterLast('/'))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The native tag parse can throw anything for a corrupt file; one bad file shouldn't fail the whole import
            Timber.w("Failed to read tags for uri: $uri (${e.javaClass.simpleName}: ${e.message})")
            null
        }
    return tags?.let { withFileTags(it) } ?: this
}

internal fun Song.withFileTags(tags: FileTags): Song = copy(
    name = tags.title ?: name,
    artists = tags.artists.ifEmpty { artists },
    albumArtist = tags.albumArtist ?: albumArtist,
    album = tags.album ?: album,
    track = tags.track ?: track,
    disc = tags.disc ?: disc,
    date = tags.year?.toIntOrNull()?.let { LocalDate(it, 1, 1) } ?: date,
    replayGainTrack = tags.replayGainTrack,
    replayGainAlbum = tags.replayGainAlbum
)
