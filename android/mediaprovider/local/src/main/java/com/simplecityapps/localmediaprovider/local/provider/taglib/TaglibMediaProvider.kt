package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FolderImage
import com.simplecityapps.localmediaprovider.local.provider.FolderImageReader
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.squareup.phrase.Phrase
import kotlin.coroutines.cancellation.CancellationException
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
    private val kTagLib: KTagLib
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        val startTime = System.currentTimeMillis()
        val files = findAudioFiles()
        if (files == null) {
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_directories_empty)))
            return@flow
        }
        val filesWithImages =
            withContext(Dispatchers.IO) {
                val folderImageReader = FolderImageReader(sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                files.map { file -> file to folderImageReader.imagesNear(file.path) }
            }
        val songs = mutableListOf<Song>()
        getSongs(filesWithImages)
            .collectIndexed { index, song ->
                emit(
                    FlowEvent.Progress(
                        MessageProgress(
                            message =
                                listOf(
                                    song.friendlyArtistName ?: song.albumArtist,
                                    song.name
                                ).joinToString(" • "),
                            progress = Progress(index, files.size)
                        )
                    )
                )
                songs.add(song)
            }
        Timber.i("Read ${songs.size} of ${files.size} MediaStore audio files in ${System.currentTimeMillis() - startTime}ms")
        emit(FlowEvent.Success(songs))
    }

    /**
     * Songs this provider stored under a SAF document URI before it found files through MediaStore, mapped to their
     * file paths. Once none are left, which is after the first import following the upgrade, it doesn't query MediaStore.
     */
    override suspend fun remapLegacySongs(existingSongs: List<Song>): List<SongPathRemap> {
        val legacySongs = existingSongs.filter { song -> legacyLocation(song.path) != null }
        if (legacySongs.isEmpty()) return emptyList()
        // Without MediaStore, findSongs fails too, so nothing is diffed and the songs keep their history until next time
        val files = findAudioFiles() ?: return emptyList()
        return LegacySafSongs(primaryStoragePath()).remaps(legacySongs, files)
            .also { remaps -> Timber.i("Matched ${remaps.size} of ${legacySongs.size} songs stored under SAF document URIs to MediaStore files") }
    }

    /**
     * The audio files MediaStore has indexed, on every volume, limited to the folders picked for the scanner if there are any.
     * Null if MediaStore can't be queried, for example without the audio permission.
     */
    private suspend fun findAudioFiles(): List<MediaStoreAudioFile>? = withContext(Dispatchers.IO) {
        val folderFilter = FolderFilter(includes = pickedFolders())
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MEDIA_STORE_AUDIO_PROJECTION,
                MEDIA_STORE_AUDIO_SELECTION,
                null,
                null
            )?.use { cursor -> cursor.readMediaStoreAudioFiles(folderFilter) }
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to query MediaStore for audio files")
            null
        }
    }

    /**
     * The paths of the folders picked with the SAF folder picker. Folders from a provider other than external storage
     * have no path, so they don't limit the import.
     */
    private fun pickedFolders(): List<String> {
        val primaryStoragePath = primaryStoragePath()
        return context.contentResolver.persistedUriPermissions
            .filter { uriPermission -> uriPermission.isReadPermission || uriPermission.isWritePermission }
            .mapNotNull { uriPermission ->
                val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uriPermission.uri) }.getOrNull() ?: return@mapNotNull null
                externalStorageTreeFolder(uriPermission.uri.authority, treeDocumentId, primaryStoragePath)
            }
    }

    @Suppress("DEPRECATION")
    private fun primaryStoragePath(): String = Environment.getExternalStorageDirectory().path

    private fun getSongs(files: List<Pair<MediaStoreAudioFile, List<FolderImage>>>): Flow<Song> = files
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { (file, folderImages) ->
            readAudioFile(file)?.toSong(type, folderImages)
        }.mapNotNull { it }

    private suspend fun readAudioFile(file: MediaStoreAudioFile): AudioFile? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(file.contentUri, "r")?.use { pfd ->
                kTagLib.getAudioFile(pfd.detachFd(), file.path, file.displayName, file.lastModified, file.size, file.mimeType)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The native tag parse can throw anything for a corrupt file; one bad file shouldn't fail the whole import
            Timber.e(e, "Failed to read audio file: ${file.contentUri} (${file.path})")
            null
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

    override fun findPlaylists(
        existingPlaylists: List<Playlist>,
        existingSongs: List<Song>
    ): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val sanitisedSongPaths = M3uEntryMatcher.sanitisedPathsByFilename(existingSongs)

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

                            M3uEntryMatcher.match(entry, sanitisedSongPaths)
                        }
                    if (songs.isNotEmpty()) {
                        val updateData =
                            MediaImporter.PlaylistUpdateData(
                                mediaProviderType = type,
                                name = m3uPlaylist.name,
                                songs = songs,
                                externalId = m3uPlaylist.path
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
