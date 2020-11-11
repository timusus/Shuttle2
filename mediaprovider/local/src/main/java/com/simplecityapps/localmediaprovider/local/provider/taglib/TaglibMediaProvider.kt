package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class TaglibMediaProvider(
    private val context: Context,
    private val fileScanner: FileScanner,
) : MediaProvider {

    var directories: List<SafDirectoryHelper.DocumentNodeTree>? = null

    private suspend fun getDocumentNodes(): List<SafDirectoryHelper.DocumentNode>? {
        return withContext(Dispatchers.IO) {
            directories?.flatMap { directory -> directory.getLeaves() }
        }
    }

    @OptIn(FlowPreview::class)
    private fun <T, R> Flow<T>.concurrentMap(concurrencyLevel: Int, transform: suspend (T) -> R): Flow<R> {
        return flatMapMerge(concurrencyLevel) { value ->
            flow { emit(transform(value)) }
        }
    }

    private fun getAudioFiles(documentNodes: List<SafDirectoryHelper.DocumentNode>): Flow<AudioFile> {
        return documentNodes
            .asFlow()
            .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) {
                fileScanner.getAudioFile(context, it.uri)!!
            }
    }

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song>? {
        return getDocumentNodes()?.let { nodes ->
            val songs = mutableListOf<Song>()
            getAudioFiles(nodes)
                .collectIndexed { index, audioFile ->
                    val song = audioFile.toSong()
                    withContext(Dispatchers.Main) {
                        callback?.invoke(song, index, nodes.size)
                    }
                    songs.add(song)
                }
            songs
        }
    }
}