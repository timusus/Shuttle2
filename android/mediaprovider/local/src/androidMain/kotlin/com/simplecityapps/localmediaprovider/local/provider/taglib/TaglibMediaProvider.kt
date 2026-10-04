package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.FolderImage
import com.simplecityapps.localmediaprovider.local.provider.FolderImageReader
import com.simplecityapps.localmediaprovider.local.provider.LocalFileTagMerger
import com.simplecityapps.localmediaprovider.local.provider.getAudioFile
import com.simplecityapps.localmediaprovider.local.provider.localArtworkVersion
import com.simplecityapps.localmediaprovider.local.provider.mountedVolumeRoots
import com.simplecityapps.localmediaprovider.local.provider.scannerUnreadableRoots
import com.simplecityapps.localmediaprovider.local.provider.toSong
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
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Collections
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
    // Whether this source's songs lack tags this build reads, which reading every file again fills in
    private val backfillFileTags: () -> Boolean = { false },
    // The folder trees the user has granted access to, which is where playlist files are looked for
    private val grantedTrees: () -> List<Uri> = { persistedTrees(context) },
    private val folders: () -> ScannerFolders
) : MediaProvider {
    override val type = MediaProviderType.Shuttle

    @Volatile
    override var unreadableRoots: Set<String> = emptySet()
        private set

    /** The playlist files the last [findSongs] walk found in each extra tree, by [treeKey], so [findPlaylists] needn't walk them again. */
    @Volatile
    private var walkedPlaylistFiles: Map<String, List<PlaylistFile>> = emptyMap()

    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        // First, so an import that fails part way doesn't leave the last one's walk for findPlaylists to use
        walkedPlaylistFiles = emptyMap()
        val startTime = System.currentTimeMillis()
        val folders = folders()
        val mediaStoreFiles = findAudioFiles(folders.filter)
        // Without MediaStore's listing (no audio permission, say), the files it would list are the stored ones: the extra
        // folders' copies of them aren't new songs
        val knownPaths = mediaStoreFiles?.map { file -> file.path } ?: existingSongs.map { song -> song.path }
        val (extraDocuments, unavailableTrees) = findExtraDocuments(folders, knownPaths = knownPaths.map { path -> path.lowercase() }.toSet())
        // With neither, failing keeps the library as it was: an empty listing would remove every song it holds
        if (mediaStoreFiles == null && extraDocuments.isEmpty()) {
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_error)))
            return@flow
        }
        val files = mediaStoreFiles.orEmpty()
        // After the listing, so a volume unmounted while it ran counts too: MediaStore leaves its songs out until it's back.
        // Songs read from a tree keep a document URI under it as their path (getExtraSongs)
        unreadableRoots = scannerUnreadableRoots(
            songPaths = existingSongs.map { song -> song.path },
            mountedRoots = mountedVolumeRoots(context),
            mediaStoreListed = mediaStoreFiles != null,
            unavailableTrees = unavailableTrees.map { treeUri -> treeUri.toString() }
        )
        val total = files.size + extraDocuments.size
        val filesWithImages =
            withContext(Dispatchers.IO) {
                val folderImageReader = FolderImageReader(sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                files.map { file -> file to folderImageReader.imagesNear(file.path) }
            }
        val songs = mutableListOf<Song>()
        val merger = LocalFileTagMerger(existingSongs, readUnchanged = backfillFileTags())
        merge(getSongs(filesWithImages, merger), getExtraSongs(extraDocuments, merger))
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
        Timber.i("Found ${songs.size} of ${files.size} MediaStore audio files and ${extraDocuments.size} extra folder files in ${System.currentTimeMillis() - startTime}ms")
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
        if (folders.extraTrees.isEmpty()) {
            walkedPlaylistFiles = emptyMap()
            return@withContext emptyList<DocumentNode>() to emptyList()
        }
        val primaryStoragePath = primaryStoragePath()
        val excludes = FolderFilter(excludes = folders.filter.excludes)
        // Read from the walks' concurrent collectors
        val excludedFolders: MutableList<DocumentNodeTree> = Collections.synchronizedList(mutableListOf())
        val statuses =
            folders.extraTrees
                .map { treeUri ->
                    // An excluded folder covers everything beneath it, so its songs aren't walked at all
                    SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, treeUri) { folder ->
                        val path = externalStorageTreeFolder(folder.uri.authority, folder.documentId, primaryStoragePath)
                        (path != null && !excludes.accepts("$path/")).also { excluded -> if (excluded) excludedFolders += folder }
                    }
                }
                .merge()
                .toList()
        val trees = statuses.filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>().map { status -> status.tree }
        // Excludes limit songs, not playlists, so the playlists in excluded folders are looked for there alone
        val excludedPlaylists =
            excludedFolders.toList().mapNotNull { folder ->
                SafDirectoryHelper.walkFolder(context.contentResolver, folder.rootUri, folder)
                    ?.let { walked -> treeKey(walked.rootUri) to walked.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
            }
        walkedPlaylistFiles =
            trees.associate { tree -> treeKey(tree.rootUri) to tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
                .let { found -> found + excludedPlaylists.filter { (key, _) -> key in found }.groupBy({ it.first }, { it.second }).mapValues { (key, lists) -> found.getValue(key) + lists.flatten() } }
        val documents =
            trees
                .flatMap { tree -> tree.getLeaves() }
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
    private fun getExtraSongs(
        documents: List<DocumentNode>,
        merger: LocalFileTagMerger
    ): Flow<Song> = documents
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { node ->
            merger.unchangedSong(node.uri.toString(), node.size, node.lastModified)?.reused(node.lastModified, emptyList())
                ?: fileScanner.getAudioFile(context, kTagLib, node)?.toSong(type, emptyList())
                // A file imported before that can't be read now keeps its song, which removing would take its play history with it
                ?: merger.existingSong(node.uri.toString())?.reused(node.lastModified, emptyList())
        }.mapNotNull { it }

    private fun getSongs(
        files: List<Pair<MediaStoreAudioFile, List<FolderImage>>>,
        merger: LocalFileTagMerger
    ): Flow<Song> = files
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { (file, folderImages) ->
            merger.unchangedSong(file.path, file.size, file.lastModified)?.reused(file.lastModified, folderImages)
                ?: readAudioFile(file)?.toSong(type, folderImages)
                ?: merger.existingSong(file.path)?.reused(file.lastModified, folderImages)
        }.mapNotNull { it }

    /** A stored song whose file is unchanged, as a freshly imported one: the tags are kept, but a cover next to the file may have changed. */
    private fun Song.reused(
        lastModified: Long,
        folderImages: List<FolderImage>
    ): Song = copy(id = 0, artworkVersion = localArtworkVersion(lastModified, folderImages))

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

    /**
     * The playlist files under each granted tree. Trees on a volume MediaStore indexes are found by one MediaStore query
     * instead of walking every document in them; the extra trees the song scan just walked reuse that walk; any other
     * tree (an extra tree, which MediaStore skips, a volume it doesn't index such as a USB drive, a cloud provider) is
     * walked here.
     */
    private suspend fun findPlaylistFiles(): List<PlaylistFile> = withContext(Dispatchers.IO) {
        val trees = grantedTrees()
        val primaryStoragePath = primaryStoragePath()
        val extraTrees = folders().extraTrees.map { tree -> treeKey(tree) }.toSet()
        // Only a tree that is still an extra tree: one dropped since the walk is looked up like any other
        val walked = walkedPlaylistFiles.filterKeys { key -> key in extraTrees }
        val mediaStoreVolumes = mediaStoreVolumes()
        // Queried once, and only if a tree needs it
        val indexed by lazy { queryPlaylistFiles() }
        trees
            .flatMap { tree ->
                val key = treeKey(tree)
                val folder = if (key in extraTrees) null else indexedTreeFolder(tree, primaryStoragePath, mediaStoreVolumes)
                walked[key]
                    ?: folder?.let { indexed?.let { files -> playlistFilesIn(tree, folder, files) } }
                    ?: walkPlaylistFiles(tree)
            }
            .distinctBy { it.uri }
    }

    /** The folder of [tree] if it's on a volume MediaStore indexes, else null. */
    private fun indexedTreeFolder(
        tree: Uri,
        primaryStoragePath: String,
        mediaStoreVolumes: Set<String>
    ): String? = try {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
        externalStorageTreeFolder(tree.authority, treeDocumentId, primaryStoragePath)
            ?.takeIf { mediaStoreIndexesTree(treeDocumentId, mediaStoreVolumes) }
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The volumes MediaStore indexes besides shared storage, by name; before Android 10 it can't say, so none. */
    private fun mediaStoreVolumes(): Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.getExternalVolumeNames(context) else emptySet()

    /** The playlist files MediaStore has indexed, by path; null if it can't be queried. */
    private fun queryPlaylistFiles(): List<IndexedPlaylistFile>? = try {
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.m3u' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.m3u8'",
            null,
            null
        )?.use { cursor ->
            val files = mutableListOf<IndexedPlaylistFile>()
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                files += IndexedPlaylistFile(path, cursor.getString(1) ?: path.substringAfterLast('/'))
            }
            files
        }
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to query MediaStore for playlist files")
        null
    }

    // Playlists are only ever added, so one in a tree that can't be read now is kept as it was
    private suspend fun walkPlaylistFiles(tree: Uri): List<PlaylistFile> = SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, tree)
        .filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>()
        .map { complete -> complete.tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
        .toList()
        .flatten()

    override fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>> = flow {
        val sanitisedSongPaths = M3uEntryMatcher.sanitisedPathsByFilename(existingSongs)

        val playlistFiles = findPlaylistFiles()
        val m3uPlaylists =
            playlistFiles
                .mapNotNull { file ->
                    try {
                        context.contentResolver.openInputStream(file.uri)
                            ?.use { inputStream ->
                                M3uParser().parse(
                                    path = file.uri.toString(),
                                    fileName = file.displayName,
                                    text = inputStream.readBytes().decodeToString()
                                )
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A MediaStore row can outlive its file, and one playlist that can't be read shouldn't stop the others
                        when (e) {
                            is FileNotFoundException, is IllegalArgumentException, is SecurityException, is IOException -> {
                                Timber.e(e, "Failed to read playlist ${file.uri}")
                                null
                            }

                            else -> throw e
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
    }
}

private data class PlaylistFile(val uri: Uri, val displayName: String)

private data class IndexedPlaylistFile(val path: String, val displayName: String)

private fun DocumentNode.isPlaylist() = ext == "m3u" || ext == "m3u8"

private fun DocumentNode.toPlaylistFile() = PlaylistFile(uri, displayName)

/** A tree by its authority and document id, which is the same however the grant that names it was encoded. */
private fun treeKey(tree: Uri): String = "${tree.authority}/${try {
    DocumentsContract.getTreeDocumentId(tree)
} catch (e: IllegalArgumentException) {
    tree.toString()
}}"

/**
 * Whether MediaStore indexes the volume holding the tree at [treeDocumentId] (`primary:Music`, `1234-5678:Music`): shared
 * storage always, any other volume (an SD card, a USB drive) only if it's among [mediaStoreVolumes], which name volumes by
 * their lowercase uuid.
 */
internal fun mediaStoreIndexesTree(
    treeDocumentId: String,
    mediaStoreVolumes: Set<String>
): Boolean {
    val root = treeDocumentId.substringBefore(':')
    return root == "primary" || root == "home" || root.lowercase() in mediaStoreVolumes
}

private fun persistedTrees(context: Context): List<Uri> = context.contentResolver.persistedUriPermissions
    .filter { permission -> permission.isReadPermission || permission.isWritePermission }
    .map { permission -> permission.uri }

/** The files in [files] under the tree at [folder], as the document URIs the tree's walk would give them. */
private fun playlistFilesIn(
    tree: Uri,
    folder: String,
    files: List<IndexedPlaylistFile>
): List<PlaylistFile> {
    val prefix = folder.trimEnd('/') + "/"
    val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
    return files
        .filter { file -> file.path.startsWith(prefix, ignoreCase = true) }
        .map { file ->
            val relative = file.path.substring(prefix.length)
            val documentId = if (treeDocumentId.endsWith(":") || treeDocumentId.endsWith("/")) "$treeDocumentId$relative" else "$treeDocumentId/$relative"
            PlaylistFile(DocumentsContract.buildDocumentUriUsingTree(tree, documentId), file.displayName)
        }
}
