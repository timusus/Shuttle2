package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.*

class TaglibSongProvider(
    private val context: Context,
    private val fileScanner: FileScanner,
) : MediaProvider {

    var directories: List<SafDirectoryHelper.DocumentNodeTree>? = null

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song>? {
        return directories?.let { directories ->
            var index = 0
            return withContext(Dispatchers.IO) {
                val uris = directories.flatMap { directory ->
                    directory.getLeaves()
                        .map { documentNode ->
                            documentNode as SafDirectoryHelper.DocumentNode
                            Pair(documentNode.uri, documentNode.mimeType)
                        }
                }
                uris.pmap { (uri, mimeType) ->
                    val song = fileScanner.getAudioFile(context, uri)?.toSong(mimeType)
                    song?.let {
                        withContext(Dispatchers.Main) {
                            callback?.invoke(song, index, uris.size)
                            index++
                        }
                    }
                    song
                }.mapNotNull { it }
            }
        }
    }
}