package com.simplecityapps.imageloading.coil.source

import android.content.Context
import android.net.Uri
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.mediaprovider.TagReadFile
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The picture embedded in the song's tags, read at full size by TagLib. */
internal class EmbeddedSongArtworkSource(
    private val context: Context,
    private val tagReader: ArtworkTagReader,
    private val tagReadGuard: TagReadGuard
) : ArtworkSource.Local<Song> {
    override suspend fun open(model: Song): InputStream? = openEmbeddedArtwork(context, tagReader, tagReadGuard, model)
}

/** The picture embedded in the album's first song. */
internal class EmbeddedAlbumArtworkSource(
    private val context: Context,
    private val tagReader: ArtworkTagReader,
    private val tagReadGuard: TagReadGuard,
    private val songRepository: SongRepository
) : ArtworkSource.Local<Album> {
    override suspend fun open(model: Album): InputStream? = songRepository.firstSongOf(model)?.let { song -> openEmbeddedArtwork(context, tagReader, tagReadGuard, song) }
}

/** The song's embedded picture, or null without reading the file if it's one [tagReadGuard] quarantined for crashing TagLib (#874). */
private suspend fun openEmbeddedArtwork(
    context: Context,
    tagReader: ArtworkTagReader,
    tagReadGuard: TagReadGuard,
    song: Song
): InputStream? {
    val uri: Uri =
        if (song.path.startsWith("content://")) {
            Uri.parse(song.path)
        } else {
            Uri.fromFile(File(song.path))
        }
    val file = TagReadFile(song.path, song.size, song.lastModified?.toEpochMilliseconds() ?: 0L)
    return tagReadGuard.read(file, song.mediaProvider) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    tagReader.read(pfd.fd, uri.lastPathSegment)?.inputStream()
                }
            } catch (e: SecurityException) {
                Timber.v("Failed to retrieve artwork (permission denial)")
                null
            } catch (e: IllegalStateException) {
                Timber.v("Failed to retrieve artwork (fd problem)")
                null
            } catch (e: FileNotFoundException) {
                Timber.v("Failed to retrieve artwork (file not found)")
                null
            }
        }
    }
}

/** Reads the picture embedded in the file open as [fd], named [fileName] for its tag format; TagLib, in the app. */
internal fun interface ArtworkTagReader {
    fun read(
        fd: Int,
        fileName: String?
    ): ByteArray?
}
