package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.R
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.*
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.squareup.phrase.Phrase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber

class TaglibMediaProvider(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
) : MediaProvider {

    override val type = MediaProviderType.Shuttle

    override fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>> {
        return flow {
            getDocumentNodes()?.let { nodes ->
                val songs = mutableListOf<Song>()
                getAudioFiles(nodes.filter { it.ext != "m3u" && it.ext != "m3u8" })
                    .collectIndexed { index, audioFile ->
                        val song = audioFile.toSong(type)
                        emit(
                            FlowEvent.Progress(
                                MessageProgress(
                                    message = listOf(
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
                emit(FlowEvent.Failure(context.getString(R.string.media_import_directories_empty)))
            }
        }
    }

    private suspend fun getDocumentNodes(): List<DocumentNode>? {
        return withContext(Dispatchers.IO) {
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
            ?.let { status ->
                status.flatMap { it.getLeaves() }
            }
    }

    private fun getAudioFiles(documentNodes: List<DocumentNode>): Flow<AudioFile> {
        return documentNodes
            .asFlow()
            .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) {
                fileScanner.getAudioFile(context, kTagLib, it.uri)
            }.mapNotNull { it }
    }

    override fun findPlaylists(
        existingPlaylists: List<Playlist>,
        existingSongs: List<Song>
    ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> {
        return flow {

            val sanitisedSongPaths = existingSongs.associateBy { Uri.decode(it.path.substringAfterLast('/')).substringAfterLast(':') }

            getDocumentNodes()?.let { nodes ->
                val m3uPlaylists = nodes
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

                val updates = m3uPlaylists.mapNotNull { m3uPlaylist ->
                    Timber.i("Importing playlist ${m3uPlaylist.name}...")
                    val songs = m3uPlaylist.entries.mapIndexedNotNull { index, entry ->
                        emit(
                            FlowEvent.Progress(
                                MessageProgress(
                                    Phrase.from(context, R.string.media_import_m3u_scan).put("playlist_name", m3uPlaylist.name).format().toString(),
                                    Progress(index, m3uPlaylist.entries.size)
                                )
                            )
                        )

                        sanitisedSongPaths.keys.firstOrNull { songPath ->
                            when {
                                songPath.equals(entry.location, ignoreCase = true) -> {
                                    true
                                }
                                songPath.length > lowercaseEntryLocation.length -> {
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
                        val updateData = MediaImporter.PlaylistUpdateData(
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
                emit(FlowEvent.Failure(context.getString(R.string.media_import_directories_empty)))
            }
        }
    }
}
