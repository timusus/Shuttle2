package com.simplecityapps.localmediaprovider.local.provider.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FileTags
import com.simplecityapps.localmediaprovider.local.provider.LocalFileTagMerger
import com.simplecityapps.localmediaprovider.local.provider.TagReadFile
import com.simplecityapps.localmediaprovider.local.provider.TagReadGuard
import com.simplecityapps.localmediaprovider.local.provider.matroskaTitleOf
import com.simplecityapps.localmediaprovider.local.provider.taglibBitDepth
import com.simplecityapps.localmediaprovider.local.provider.toFileTags
import com.simplecityapps.localmediaprovider.local.provider.toYearDate
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Reads the tags of [file] through [uri]. Returns null if TagLib can't parse the file or it's quarantined, and may throw
 * if it can't be opened; callers treat each as "no tags".
 */
fun interface MediaStoreTagReader {
    suspend fun read(
        uri: Uri,
        file: TagReadFile
    ): FileTags?
}

class KTagLibMediaStoreTagReader(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val tagReadGuard: TagReadGuard
) : MediaStoreTagReader {
    override suspend fun read(
        uri: Uri,
        file: TagReadFile
    ): FileTags? = tagReadGuard.read(file, MediaProviderType.MediaStore) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fileName = file.path.substringAfterLast('/')
                kTagLib.getMetadata(pfd.fd, fileName)?.let { metadata ->
                    val audio = metadata.audioProperties
                    metadata.propertyMap?.toFileTags()?.let { tags ->
                        tags.copy(
                            // TagLib's property map has no Matroska segment title (#523)
                            title = tags.title ?: matroskaTitleOf(pfd.fd, fileName),
                            bitRate = audio?.bitrate,
                            bitDepth = taglibBitDepth(audio?.codec, audio?.bitsPerSample),
                            sampleRate = audio?.sampleRate,
                            channelCount = audio?.channelCount
                        )
                    }
                }
            }
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
    val merger = LocalFileTagMerger(existingSongs, readUnchanged)
    return asFlow().concurrentMap(concurrency) { song ->
        val existingSong = song.lastModified?.let { merger.unchangedSong(song.path, song.size, it.toEpochMilliseconds()) }
        if (existingSong != null) {
            with(merger) { song.withStoredTags(existingSong) }
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
            reader.read(uri = uri, file = TagReadFile(path, size, lastModified?.toEpochMilliseconds() ?: 0))
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
    date = tags.year?.toYearDate() ?: date,
    replayGainTrack = tags.replayGainTrack,
    replayGainAlbum = tags.replayGainAlbum,
    genres = tags.genres.ifEmpty { genres },
    lyrics = tags.lyrics ?: lyrics,
    grouping = tags.grouping ?: grouping,
    bitRate = tags.bitRate ?: bitRate,
    bitDepth = tags.bitDepth ?: bitDepth,
    sampleRate = tags.sampleRate ?: sampleRate,
    channelCount = tags.channelCount ?: channelCount,
    albumArtists = tags.albumArtists,
    artistsTag = tags.artistsTag,
    artistDisplay = tags.artistDisplay ?: artistDisplay,
    compilation = tags.compilation,
    mbTrackId = tags.mbTrackId,
    mbAlbumId = tags.mbAlbumId,
    mbReleaseGroupId = tags.mbReleaseGroupId,
    mbArtistIds = tags.mbArtistIds,
    mbAlbumArtistIds = tags.mbAlbumArtistIds
)
