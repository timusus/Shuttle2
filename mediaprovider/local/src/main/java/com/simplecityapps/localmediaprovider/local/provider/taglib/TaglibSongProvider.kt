package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.SongProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.taglib.FileScanner
import io.reactivex.Observable

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaglibSongProvider

        if (context != other.context) return false
        if (fileScanner != other.fileScanner) return false
        if (directories != other.directories) return false

        return true
    }

    override fun hashCode(): Int {
        var result = context.hashCode()
        result = 31 * result + fileScanner.hashCode()
        result = 31 * result + directories.hashCode()
        return result
    }

    companion object {
        const val TAG = "LocalMediaProvider"

        init {
            System.loadLibrary("file-scanner")
        }
    }
}