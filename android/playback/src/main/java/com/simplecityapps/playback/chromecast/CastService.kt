package com.simplecityapps.playback.chromecast

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.query.SongQuery
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * What [HttpServer] serves a Cast receiver: a local song's file and any song's artwork, and for a remote-provider
 * song, where its server streams it from.
 */
class CastService(
    private val context: Context,
    private val songRepository: SongRepository,
    private val artworkImageLoader: ArtworkImageLoader,
    private val mediaInfoProvider: MediaInfoProvider
) {
    class AudioStream(val stream: InputStream, val length: Long, val mimeType: String)

    /**
     * Where a remote-provider song streams from, resolved as the receiver asks for it; null for a local song, or one
     * that isn't in the library.
     */
    suspend fun getRemoteAudioUrl(songId: Long): String? = songRepository.getSongs(SongQuery.SongIds(listOf(songId))).firstOrNull()?.firstOrNull()?.let { song ->
        mediaInfoProvider.getMediaInfo(song).takeIf { it.isRemote }?.path?.toString()
    }

    suspend fun getArtwork(songId: Long): ByteArray? = songRepository.getSongs(SongQuery.SongIds(listOf(songId))).firstOrNull()?.firstOrNull()?.let { song ->
        artworkImageLoader.loadBitmap(song)
    }

    suspend fun getAudio(songId: Long): AudioStream? = withContext(Dispatchers.IO) {
        songRepository.getSongs(SongQuery.SongIds(listOf(songId))).firstOrNull()?.firstOrNull()?.let { song ->
            val uri = Uri.parse(song.path)
            if (song.path.startsWith("content://")) {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    documentFile?.let {
                        if (it.exists()) {
                            try {
                                context.contentResolver.openInputStream(documentFile.uri)?.let { inputStream ->
                                    AudioStream(inputStream, documentFile.length(), documentFile.type ?: "audio/*")
                                }
                            } catch (e: FileNotFoundException) {
                                Timber.e(e, "Failed to retrieve audio from songId: $songId")
                                null
                            }
                        } else {
                            Timber.e("Failed to retrieve audio from songId: $songId (Document file doesn't exist)")
                            null
                        }
                    }
                } else {
                    try {
                        context.contentResolver.openInputStream(uri)?.let { inputStream ->
                            AudioStream(inputStream, song.size, song.mimeType)
                        }
                    } catch (e: FileNotFoundException) {
                        Timber.e(e, "Failed to retrieve audio from songId: $songId")
                        null
                    }
                }
            } else {
                try {
                    AudioStream(File(uri.toString()).inputStream(), song.size, song.mimeType)
                } catch (e: FileNotFoundException) {
                    Timber.e(e, "Failed to retrieve audio from songId: $songId")
                    null
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to retrieve audio from songId: $songId")
                    null
                }
            }
        }
    }
}
