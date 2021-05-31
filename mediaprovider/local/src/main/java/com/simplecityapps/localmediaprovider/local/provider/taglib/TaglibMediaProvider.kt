package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

class TaglibMediaProvider(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
) : MediaProvider {

    override val type: MediaProvider.Type
        get() = MediaProvider.Type.Shuttle

    private suspend fun getDocumentNodes(): List<SafDirectoryHelper.DocumentNode>? {
        return withContext(Dispatchers.IO) {
            context.contentResolver?.persistedUriPermissions
                ?.filter { uriPermission -> uriPermission.isReadPermission || uriPermission.isWritePermission }
                ?.map { uriPermission -> SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, uriPermission.uri).distinctUntilChanged() }
                ?.merge()
                ?.toList()
                ?.let { directories ->
                    directories.flatMap { it.getLeaves() }
                }
        }
    }

    private fun getAudioFiles(documentNodes: List<SafDirectoryHelper.DocumentNode>): Flow<AudioFile> {
        return documentNodes
            .asFlow()
            .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) {
                fileScanner.getAudioFile(context, kTagLib, it.uri)
            }.mapNotNull { it }
    }

    override suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> Unit)?): List<Song>? {
        return getDocumentNodes()?.let { nodes ->
            val songs = mutableListOf<Song>()
            getAudioFiles(nodes)
                .collectIndexed { index, audioFile ->
                    val song = audioFile.toSong(type)
                    withContext(Dispatchers.Main) {
                        callback?.invoke(song, index + 1, nodes.size)
                    }
                    songs.add(song)
                }
            songs
        }
    }
}