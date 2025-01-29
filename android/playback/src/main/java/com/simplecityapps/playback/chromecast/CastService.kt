package com.simplecityapps.playback.chromecast

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.query.SongQuery
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

class CastService(
    private val context: Context,
    private val songRepository: SongRepository,
    private val artworkImageLoader: ArtworkImageLoader
) {
    class AudioStream(val stream: InputStream, val length: Long, val mimeType: String)

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
