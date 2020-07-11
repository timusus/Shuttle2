package com.simplecityapps.playback.chromecast

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class CastService(
    private val context: Context,
    private val songRepository: SongRepository
) {

    private val imageLoader: ArtworkImageLoader

    init {
        imageLoader = GlideImageLoader(context)
    }

    class AudioStream(val stream: InputStream, val length: Long, val mimeType: String)

    suspend fun getArtwork(songId: Long): ByteArray? {
        return songRepository.getSongs(SongQuery.SongIds(listOf(songId))).firstOrNull()?.firstOrNull()?.let { song ->
            imageLoader.loadBitmap(song)
        }
    }

    suspend fun getAudio(songId: Long): AudioStream? {
        return withContext(Dispatchers.IO) {
            songRepository.getSongs(SongQuery.SongIds(listOf(songId))).firstOrNull()?.firstOrNull()?.let { song ->
                val uri = Uri.parse(song.path)
                if (song.path.startsWith("content://")) {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        val documentFile = DocumentFile.fromSingleUri(context, uri)
                        documentFile?.let {
                            context.contentResolver.openInputStream(documentFile.uri)?.let { inputStream ->
                                AudioStream(inputStream, documentFile.length(), documentFile.type ?: "audio/*")
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.let { inputStream ->
                            AudioStream(inputStream, song.size, song.mimeType)
                        }
                    }
                } else {
                    AudioStream(File(uri.toString()).inputStream(), song.size, song.mimeType)
                }
            }
        }
    }
}