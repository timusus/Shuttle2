package com.simplecityapps.playback.chromecast

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository
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

    fun getArtwork(songId: Long): ByteArray? {
        songRepository.getSongs(SongQuery.SongIds(listOf(songId))).blockingFirst().firstOrNull()?.let { song ->
            return imageLoader.loadBitmap(song)
        }

        return null
    }

    fun getAudio(songId: Long): AudioStream? {
        songRepository.getSongs(SongQuery.SongIds(listOf(songId))).blockingFirst().firstOrNull()?.let { song ->
            val uri = Uri.parse(song.path)
            return if (song.path.startsWith("content://")) {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val documentFile = DocumentFile.fromSingleUri(context, uri)
                    documentFile?.let {
                        context.contentResolver.openInputStream(documentFile.uri)?.let { inputStream ->
                            return AudioStream(inputStream, documentFile.length(), documentFile.type ?: "audio/*")
                        }
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.let { inputStream ->
                        return AudioStream(inputStream, song.size, song.mimeType)
                    }
                }
            } else {
                return AudioStream(File(uri.toString()).inputStream(), song.size, song.mimeType)
            }
        }
        return null
    }
}