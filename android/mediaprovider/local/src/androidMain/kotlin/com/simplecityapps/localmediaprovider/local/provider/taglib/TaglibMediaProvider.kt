package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FolderImage
import com.simplecityapps.localmediaprovider.local.provider.FolderImageReader
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.localmediaprovider.local.provider.mountedVolumeRoots
import com.simplecityapps.localmediaprovider.local.provider.toSong
import com.simplecityapps.localmediaprovider.local.provider.unmountedRoots
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
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

/**
 * The folders the S2 scanner covers, read at the start of each import: [filter] limits MediaStore's audio rows, and
 * [extraTrees] are SAF trees walked directly, for folders MediaStore skips (`.nomedia`) or formats it doesn't index.
 */
data class ScannerFolders(
    val filter: FolderFilter = FolderFilter(),
    val extraTrees: List<Uri> = emptyList()
)

class TaglibMediaProvider(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
    private val folders: () -> ScannerFolders
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    @Volatile
    override var unreadableRoots: Set<String> = emptySet()
        private set

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        val startTime = System.currentTimeMillis()
        val folders = folders()
        // Without MediaStore's listing, failing keeps the library as it was: an empty one would remove every song it holds
        val files =
            findAudioFiles(folders.filter) ?: run {
                emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_error)))
                return@flow
            }
        val (extraDocuments, unavailableTrees) = findExtraDocuments(folders, knownPaths = files.map { it.path.lowercase() }.toSet())
        // After the listing, so a volume unmounted while it ran counts too: MediaStore leaves its songs out until it's back.
        // Songs read from a tree keep a document URI under it as their path (getExtraSongs)
        unreadableRoots = unmountedRoots(existingSongs.map { song -> song.path }, mountedVolumeRoots(context)) +
            unavailableTrees.map { treeUri -> "$treeUri/document/" }
        val total = files.size + extraDocuments.size
        val filesWithImages =
            withContext(Dispatchers.IO) {
                val folderImageReader = FolderImageReader(sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                files.map { file -> file to folderImageReader.imagesNear(file.path) }
            }
        val songs = mutableListOf<Song>()
        merge(getSongs(filesWithImages), getExtraSongs(extraDocuments))
            .collectIndexed { index, song ->
                emit(
                    FlowEvent.Progress(
                        MessageProgress(
                            phase = ImportPhase.Fetching,
                            detail =
                                listOf(
                                    song.friendlyArtistName ?: song.albumArtist,
                                    song.name
                                ).joinToString(" • "),
                            progress = Progress(index, total)
                        )
                    )
                )
                songs.add(song)
            }
        Timber.i("Read ${songs.size} of ${files.size} MediaStore audio files and ${extraDocuments.size} extra folder files in ${System.currentTimeMillis() - startTime}ms")
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
        val files = findAudioFiles(folders().filter) ?: return emptyList()
        return LegacySafSongs(primaryStoragePath()).remaps(legacySongs, files)
            .also { remaps -> Timber.i("Matched ${remaps.size} of ${legacySongs.size} songs stored under SAF document URIs to MediaStore files") }
    }

    /**
     * The audio files MediaStore has indexed, on every volume, limited by [folderFilter].
     * Null if MediaStore can't be queried, for example without the audio permission.
     */
    private suspend fun findAudioFiles(folderFilter: FolderFilter): List<MediaStoreAudioFile>? = withContext(Dispatchers.IO) {
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
     * The audio documents in the extra folders that MediaStore didn't already list (by [knownPaths], lowercased), and
     * that no excluded folder covers, and the extra folders that couldn't be read.
     */
    private suspend fun findExtraDocuments(
        folders: ScannerFolders,
        knownPaths: Set<String>
    ): Pair<List<DocumentNode>, List<Uri>> = withContext(Dispatchers.IO) {
        if (folders.extraTrees.isEmpty()) return@withContext emptyList<DocumentNode>() to emptyList()
        val primaryStoragePath = primaryStoragePath()
        val excludes = FolderFilter(excludes = folders.filter.excludes)
        val statuses =
            folders.extraTrees
                .map { treeUri -> SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, treeUri) }
                .merge()
                .toList()
        val documents =
            statuses
                .filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>()
                .flatMap { status -> status.tree.getLeaves() }
                .filter { node -> node.ext != "m3u" && node.ext != "m3u8" && node.ext != "pls" }
                .filter { node ->
                    val path = externalStorageTreeFolder(node.uri.authority, node.documentId, primaryStoragePath) ?: return@filter true
                    path.lowercase() !in knownPaths && excludes.accepts(path)
                }
                .distinctBy { node -> node.uri }
        documents to statuses.filterIsInstance<SafDirectoryHelper.TreeStatus.Unavailable>().map { status -> status.rootUri }
    }

    @Suppress("DEPRECATION")
    private fun primaryStoragePath(): String = Environment.getExternalStorageDirectory().path

    /** Songs read from SAF documents keep their document URI as their path, which the tag editor writes through. */
    private fun getExtraSongs(documents: List<DocumentNode>): Flow<Song> = documents
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { node ->
            fileScanner.getAudioFile(context, kTagLib, node.uri)?.toSong(type, emptyList())
        }.mapNotNull { it }

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

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val sanitisedSongPaths = M3uEntryMatcher.sanitisedPathsByFilename(existingSongs)

        getDocumentTrees()?.flatMap { tree -> tree.getLeaves() }?.let { nodes ->
            val m3uPlaylists =
                nodes
                    .filter { it.ext == "m3u" || it.ext == "m3u8" }
                    .mapNotNull { documentNode ->
                        context.contentResolver.openInputStream(documentNode.uri)
                            ?.use { inputStream ->
                                M3uParser().parse(
                                    path = documentNode.uri.toString(),
                                    fileName = documentNode.displayName,
                                    text = inputStream.readBytes().decodeToString()
                                )
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
                                        phase = ImportPhase.Fetching,
                                        progress = Progress(index, m3uPlaylist.entries.size),
                                        detail = context.getString(com.simplecityapps.mediaprovider.R.string.media_import_m3u_scan, m3uPlaylist.name)
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
