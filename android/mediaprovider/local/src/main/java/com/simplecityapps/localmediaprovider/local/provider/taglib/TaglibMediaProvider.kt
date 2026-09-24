package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FolderImage
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.squareup.phrase.Phrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import timber.log.Timber

class TaglibMediaProvider(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        getDocumentTrees()?.let { trees ->
            val nodes =
                trees
                    .flatMap { tree -> tree.leavesWithFolderImages() }
                    .filter { (node, _) -> node.ext != "m3u" && node.ext != "m3u8" && node.ext != "pls" }
            val songs = mutableListOf<Song>()
            getSongs(nodes)
                .collectIndexed { index, song ->
                    emit(
                        FlowEvent.Progress(
                            MessageProgress(
                                message =
                                    listOf(
                                        song.friendlyArtistName ?: song.albumArtist,
                                        song.name
                                    ).joinToString(" • "),
                                progress = Progress(index, nodes.size)
                            )
                        )
                    )
                    songs.add(song)
                }
            emit(FlowEvent.Success(songs))
        } ?: run {
            Timber.e("No document nodes to scan")
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_directories_empty)))
        }
    }

    private suspend fun getDocumentTrees(): List<DocumentNodeTree>? = withContext(Dispatchers.IO) {
        context.contentResolver?.persistedUriPermissions
            ?.filter { uriPermission -> uriPermission.isReadPermission || uriPermission.isWritePermission }
            ?.map { uriPermission ->
                SafDirectoryHelper.buildFolderNodeTree(
                    context.contentResolver,
                    uriPermission.uri
                )
                    .filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>()
                    .map { it.tree }
            }
    }
        ?.merge()
        ?.toList()

    private fun getSongs(documentNodes: List<Pair<DocumentNode, List<FolderImage>>>): Flow<Song> = documentNodes
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { (node, folderImages) ->
            fileScanner.getAudioFile(context, kTagLib, node.uri)?.toSong(type, folderImages)
        }.mapNotNull { it }

    override fun findPlaylists(
        existingPlaylists: List<Playlist>,
        existingSongs: List<Song>
    ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val sanitisedSongPaths = existingSongs.associateBy { Uri.decode(it.path.substringAfterLast('/')).substringAfterLast(':') }

        getDocumentTrees()?.flatMap { tree -> tree.getLeaves() }?.let { nodes ->
            val m3uPlaylists =
                nodes
                    .filter { it.ext == "m3u" || it.ext == "m3u8" }
                    .mapNotNull { documentNode ->
                        context.contentResolver.openInputStream(documentNode.uri)
                            .use { inputStream ->
                                inputStream?.let {
                                    M3uParser().parse(
                                        path = documentNode.uri.toString(),
                                        fileName = documentNode.displayName,
                                        inputStream = inputStream
                                    )
                                }
                            }
                    }

            val updates =
                m3uPlaylists.mapNotNull { m3uPlaylist ->
                    Timber.i("Importing playlist ${m3uPlaylist.name}...")
                    val songs =
                        m3uPlaylist.entries.mapIndexedNotNull { index, entry ->
                            emit(
                                FlowEvent.Progress(
                                    MessageProgress(
                                        Phrase.from(context, com.simplecityapps.mediaprovider.R.string.media_import_m3u_scan).put("playlist_name", m3uPlaylist.name).format().toString(),
                                        Progress(index, m3uPlaylist.entries.size)
                                    )
                                )
                            )

                            sanitisedSongPaths.keys.firstOrNull { songPath ->
                                when {
                                    songPath.equals(entry.location, ignoreCase = true) -> {
                                        true
                                    }

                                    songPath.length > entry.location.length -> {
                                        songPath.contains(other = entry.location, ignoreCase = true)
                                    }

                                    else -> {
                                        entry.location.contains(other = songPath, ignoreCase = true)
                                    }
                                }
                            }?.let { matchingPath ->
                                sanitisedSongPaths[matchingPath]
                            }
                        }
                    if (songs.isNotEmpty()) {
                        val updateData =
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = m3uPlaylist.name,
                                songs = songs,
                                externalId = m3uPlaylist.name
                            )
                        updateData
                    } else {
                        null
                    }
                }
            emit(FlowEvent.Success(updates.toList()))
        } ?: run {
            Timber.e("No document nodes to scan")
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_directories_empty)))
        }
    }
}

/**
 * Pairs each audio leaf with the images in its directory and the directory above it, for the song's artwork version.
 */
internal fun DocumentNodeTree.leavesWithFolderImages(parentImages: List<FolderImage> = emptyList()): List<Pair<DocumentNode, List<FolderImage>>> {
    val images = imageNodes.map { node -> FolderImage(node.displayName, node.lastModified, node.size) }
    return leafNodes.map { leaf -> leaf to images + parentImages } +
        treeNodes.flatMap { child -> child.leavesWithFolderImages(parentImages = images) }
}
