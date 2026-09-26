package com.simplecityapps.imageloading.coil.source

import android.content.Context
import android.net.Uri
import com.simplecityapps.imageloading.coil.ArtworkSource
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.Song
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import timber.log.Timber

/** The picture embedded in the song's tags, read at full size by TagLib. */
internal class EmbeddedSongArtworkSource(
    private val context: Context,
    private val kTagLib: KTagLib
) : ArtworkSource.Local<Song> {
    override suspend fun open(model: Song): InputStream? = openEmbeddedArtwork(context, kTagLib, model)
}

/** The picture embedded in the album's first song. */
internal class EmbeddedAlbumArtworkSource(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val songRepository: SongRepository
) : ArtworkSource.Local<Album> {
    override suspend fun open(model: Album): InputStream? = songRepository.firstSongOf(model)?.let { song -> openEmbeddedArtwork(context, kTagLib, song) }
}

private fun openEmbeddedArtwork(
    context: Context,
    kTagLib: KTagLib,
    song: Song
): InputStream? {
    val uri: Uri =
        if (song.path.startsWith("content://")) {
            Uri.parse(song.path)
        } else {
            Uri.fromFile(File(song.path))
        }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            kTagLib.getArtwork(pfd.detachFd(), uri.lastPathSegment)?.inputStream()
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
