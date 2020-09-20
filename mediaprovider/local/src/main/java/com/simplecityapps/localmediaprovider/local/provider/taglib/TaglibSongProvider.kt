package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class TaglibSongProvider(
    private val context: Context,
    private val fileScanner: FileScanner,
) : MediaProvider {

    var directories: List<SafDirectoryHelper.DocumentNodeTree>? = null

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song>? {
        return directories?.let { directories ->
            val songs = mutableListOf<Song>()
            withContext(Dispatchers.IO) {
                directories.flatMap { directory ->
                    directory.getLeaves()
                        .map { documentNode ->
                            documentNode as SafDirectoryHelper.DocumentNode
                            Pair(documentNode.uri, documentNode.mimeType)
                        }
                }.apply {
                    forEachIndexed { index, (uri, mimeType) ->
                        if (coroutineContext.isActive) {
                            fileScanner.getAudioFile(context, uri)?.toSong(mimeType)?.let { song ->
                                withContext(Dispatchers.Main) {
                                    callback?.invoke(song, index, size)
                                }
                                songs.add(song)
                            }
                        }
                    }
                }
                songs.sortedWith(SongSortOrder.ArtistName.comparator)
            }
        }
    }
}