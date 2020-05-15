package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.ktaglib.AudioFile
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import io.reactivex.Observable
import timber.log.Timber
import java.util.*

class TaglibSongProvider(
    private val context: Context,
    private val fileScanner: FileScanner,
    private val directories: List<SafDirectoryHelper.DocumentNodeTree>
) : SongProvider {

    override fun findSongs(): Observable<Pair<Song, Float>> {
        var progress = 0

        return Observable.create { emitter ->
            directories.flatMap { directory ->
                directory.getLeaves()
                    .map { documentNode ->
                        documentNode as SafDirectoryHelper.DocumentNode
                        Pair(documentNode.uri, documentNode.mimeType)
                    }
            }.apply {
                forEach { pair ->
                    if (emitter.isDisposed) {
                        return@forEach
                    }

                    fileScanner.getAudioFile(context, pair.first)?.toSong(pair.second)?.let { songData ->
                        emitter.onNext(Pair(songData, progress / size.toFloat()))
                    }
                    progress++
                }
            }

            emitter.onComplete()
        }
    }

    companion object {
        const val TAG = "LocalMediaProvider"
    }
}


class FileScanner(private val tagLib: KTagLib) {

    fun getAudioFile(context: Context, uri: Uri): AudioFile? {
        DocumentFile.fromSingleUri(context, uri)?.let { documentFile ->
            try {
                context.contentResolver.openFileDescriptor(documentFile.uri, "r")?.use { pfd ->
                    val audioFile = tagLib.getAudioFile(pfd.fd, uri.toString(), documentFile.name?.substringBeforeLast(".") ?: "Unknown")
                    if (audioFile != null) {
                        return audioFile
                    } else {
                        Calendar.getInstance().time

                        return AudioFile(
                            path = uri.toString(),
                            size = documentFile.length(),
                            lastModified = documentFile.lastModified(),
                            title = documentFile.name?.substringBeforeLast("."),
                            albumArtist = null,
                            artist = null,
                            album = documentFile.parentFile?.name,
                            track = 1,
                            trackTotal = 1,
                            disc = 1,
                            discTotal = 1,
                            duration = 0,
                            year = Calendar.getInstance().apply { time = Date(documentFile.lastModified()) }.get(Calendar.YEAR),
                            genre = null
                        )
                    }
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e, "Failed to retrieve audio file for uri: $uri")
            }
        }

        return null
    }
}