package com.simplecityapps.playback.chromecast

import android.content.Context
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongRepository

class CastService(
    private val context: Context,
    private val songRepository: SongRepository,
    private val imageLoader: ArtworkImageLoader
) {

    fun getArtwork(songId: Long): ByteArray? {
        songRepository.getSongs(SongQuery.SongIds(listOf(songId))).blockingFirst().firstOrNull()?.let { song ->
            return imageLoader.loadBitmap(song)
        }

        return null
    }

//    fun getAudio(songId: Long): InputStream? {
//        songRepository.getSongs(SongQuery.SongIds(listOf(songId))).blockingFirst().firstOrNull()?.let { song ->
//            val uri = Uri.parse(song.path)
//            return if (song.path.startsWith("content://")) {
//                if (DocumentsContract.isDocumentUri(context, uri)) {
//                    val documentFile = DocumentFile.fromSingleUri(context, uri)
//                    val length = documentFile.length()
//                    val mimeType = documentFile?.type
//                } else {
//                    val length = context.contentResolver.
//                    val mimeType = context.contentResolver.getType(uri)
//                }
//                context.contentResolver.openInputStream(uri)
//            } else {
//                val file = File(uri.toString())
//                val length = song.size
//                val mimeType = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
//                file.inputStream()
//            }
//        }
//
//        return null
//    }
}