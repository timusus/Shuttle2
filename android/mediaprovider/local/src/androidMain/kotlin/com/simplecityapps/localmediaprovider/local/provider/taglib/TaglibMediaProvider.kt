package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentUris
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
import com.simplecityapps.mediaprovider.IndexedMediaProvider
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongPathRemap
import com.simplecityapps.mediaprovider.TagReadFile
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.DocumentNodeTree
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.coroutines.concurrentMap
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.storage.documentIdForPath
import java.io.File
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
 * The folders the S2 scanner covers, read at the start of each import: [filter] limits MediaStore's audio rows;
 * [includeTrees] are the SAF trees of the included folders, walked when MediaStore's listing of them can't be trusted
 * alone; and [extraTrees] are SAF trees always walked directly, for folders MediaStore skips (`.nomedia`) or formats it
 * doesn't index.
 */
data class ScannerFolders(
    val filter: FolderFilter = FolderFilter(),
    val includeTrees: List<Uri> = emptyList(),
    val extraTrees: List<Uri> = emptyList()
)

class TaglibMediaProvider(
    private val context: Context,
    private val kTagLib: KTagLib,
    private val fileScanner: FileScanner,
    // Caps the native reads of every flow findSongs merges, and leaves out the files that crashed one
    private val tagReadGuard: TagReadGuard,
    // Whether this source's songs lack tags this build reads, which reading every file again fills in
    private val backfillFileTags: () -> Boolean = { false },
    // The folder trees the user has granted access to, which is where playlist files are looked for
    private val grantedTrees: () -> List<Uri> = { persistedTrees(context) },
    // The roots of the storage volumes mounted now, each ending in a separator
    private val mountedRoots: () -> Set<String> = { mountedVolumeRoots(context) },
    // MediaStore's audio files, read only in part where the last stored import's listing allows (#875)
    private val mediaStoreFiles: MediaStoreAudioLister = MediaStoreAudioLister.whole(context),
    private val folders: () -> ScannerFolders
) : IndexedMediaProvider {
    override val type = MediaProviderType.Shuttle

    @Volatile
    override var unreadableRoots: Set<String> = emptySet()
        private set

    @Volatile
    override var skippedFiles: Set<String> = emptySet()
        private set

    /** The playlist files the last [findSongs] walk found in each tree it walked, by [treeKey], so [findPlaylists] needn't walk them again. */
    @Volatile
    private var walkedPlaylistFiles: Map<String, List<PlaylistFile>> = emptyMap()

    /**
     * The MediaStore listing [remapLegacySongs] read, for the [findSongs] that follows it in the same import to take instead
     * of querying again (#869): a file listed by one query and not the other would be moved by the remap and then found
     * under its other path, taking its history with it.
     */
    @Volatile
    private var remapListing: MediaStoreListing? = null

    /** MediaStore's listing, walking only the included folders it lists nothing in. */
    override fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, thorough = false)

    /** MediaStore's listing and a walk of every included folder, for the files MediaStore skipped or lost. */
    override fun findSongsThoroughly(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>> = findSongs(existingSongs, thorough = true)

    /**
     * MediaStore's audio files and those of the include trees walked this time: every one if [thorough] or if MediaStore
     * can't be listed, else those on a volume MediaStore indexes that it lists nothing in. A file both find is one song,
     * matched by its file path; a song only a walk found keeps its document URI as its path, which is how it's read (a
     * file MediaStore skips can't be opened by its path). A stored song in an include tree that neither found nor walked is
     * looked up on its own, so a song only a walk found stays until its file is gone.
     */
    private fun findSongs(
        existingSongs: List<Song>,
        thorough: Boolean
    ): Flow<FlowEvent<List<Song>, MessageProgress>> = flow {
        // First, so an import that fails part way doesn't leave the last one's walk for findPlaylists to use
        walkedPlaylistFiles = emptyMap()
        skippedFiles = emptySet()
        tagReadGuard.recover(type)
        val startTime = System.currentTimeMillis()
        val folders = folders()
        val primaryStoragePath = primaryStoragePath()
        // A thorough import reads MediaStore whole too, rather than trusting the last listing for the rows it didn't read
        val mediaStoreFiles = takeRemapListing(folders.filter).let { listing -> if (listing != null) listing.files else findAudioFiles(folders.filter, whole = thorough) }
        // A listing with no files in it at all is MediaStore reindexing, not a library gone: a song stored under its file
        // path keeps it (remapLegacySongs leaves it there), for the next listing to find there again
        val keepFilePaths = mediaStoreFiles?.isEmpty() == true
        val extraKeys = folders.extraTrees.map { tree -> treeKey(tree) }.toSet()
        val includeTrees = folders.includeTrees.filter { tree -> treeKey(tree) !in extraKeys }
        val walk = walkTrees(includeTreesToWalk(includeTrees, mediaStoreFiles, thorough, primaryStoragePath), folders, primaryStoragePath)
        val mediaStorePaths = mediaStoreFiles?.mapTo(HashSet()) { file -> file.path.lowercase() }
        // A file MediaStore lists is read from its listing, so the walk adds only those it left out
        val walkedFiles = walk.includeFiles.filter { file -> mediaStorePaths?.contains(file.key.lowercase()) != true }
        // Without MediaStore's listing (no audio permission, say), the files it would list are the stored ones: the extra
        // folders' copies of them aren't new songs
        val knownPaths = (mediaStorePaths ?: existingSongs.mapTo(HashSet()) { song -> song.path.lowercase() }) + walk.includeFiles.map { file -> file.key.lowercase() }
        val extraDocuments =
            walk.extraDocuments.filter { node ->
                val path = externalStorageTreeFolder(node.uri.authority, node.documentId, primaryStoragePath) ?: return@filter true
                path.lowercase() !in knownPaths
            }
        // With none of them, failing keeps the library as it was: an empty listing would remove every song it holds
        if (mediaStoreFiles == null && walkedFiles.isEmpty() && extraDocuments.isEmpty()) {
            emit(FlowEvent.Failure(context.getString(com.simplecityapps.mediaprovider.R.string.media_import_error)))
            return@flow
        }
        val files = mediaStoreFiles.orEmpty()
        // After the listing, so a volume unmounted while it ran counts too: MediaStore leaves its songs out until it's back.
        // Without MediaStore, a complete walk of every included folder lists every file the filter takes. Songs read from
        // an extra tree keep a document URI under it as their path (getExtraSongs)
        unreadableRoots = scannerUnreadableRoots(
            songPaths = existingSongs.map { song -> song.path },
            mountedRoots = mountedRoots(),
            mediaStoreListed = mediaStoreFiles != null || (includeTrees.isNotEmpty() && includeTrees.all { tree -> treeKey(tree) in walk.completeTrees }),
            unavailableTrees = walk.unavailableExtraTrees.map { treeUri -> treeUri.toString() }
        )
        val unwalkedTrees = includeTrees.filter { tree -> treeKey(tree) !in walk.completeTrees }
        val listedPaths = mediaStorePaths.orEmpty() + walkedFiles.map { file -> file.key.lowercase() }
        val unlisted =
            existingSongs.mapNotNull { song ->
                val filePath = songFilePath(song.path, primaryStoragePath)
                // A song under a root that couldn't be read is kept as it is by the delete guard
                if ((filePath ?: song.path).lowercase() in listedPaths || unreadableRoots.any { root -> song.path.startsWith(root) }) return@mapNotNull null
                if (filePath != null && !folders.filter.accepts(filePath)) return@mapNotNull null
                includeDocument(song.path, unwalkedTrees, primaryStoragePath)?.let { document -> song to document }
            }
        // The include trees a lookup found this app can no longer read, whose songs the delete guard keeps
        val lostTrees: MutableSet<Uri> = Collections.synchronizedSet(mutableSetOf())
        val total = files.size + walkedFiles.size + extraDocuments.size + unlisted.size
        val folderImageReader = FolderImageReader(sharedStorageListsImages = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        val filesWithImages = withContext(Dispatchers.IO) { files.map { file -> file to folderImageReader.imagesNear(file.path) } }
        val songs = mutableListOf<Song>()
        val merger = LocalFileTagMerger(existingSongs, readUnchanged = backfillFileTags())
        merge(
            getSongs(filesWithImages, merger),
            getWalkedSongs(walkedFiles, merger, folderImageReader, keepFilePaths),
            getExtraSongs(extraDocuments, merger),
            getLookedUpSongs(unlisted, merger, folderImageReader, lostTrees)
        ).collectIndexed { index, song ->
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
        if (lostTrees.isNotEmpty()) {
            Timber.w("Lost access to ${lostTrees.size} included folders: $lostTrees")
            unreadableRoots = unreadableRoots + lostTrees.flatMap { tree -> treeRoots(tree, primaryStoragePath) }
        }
        Timber.i(
            "Found ${songs.size} of ${files.size} MediaStore audio files, ${walkedFiles.size} more in included folders, ${extraDocuments.size} extra folder " +
                "files and ${unlisted.size} stored songs MediaStore didn't list (thorough: $thorough) in ${System.currentTimeMillis() - startTime}ms"
        )
        skippedFiles = tagReadGuard.skippedPaths(type)
        emit(FlowEvent.Success(songs))
    }

    /** Keeps the MediaStore listing the stored songs were found from, for the next import to read only what changed since. */
    override suspend fun songsStored() {
        mediaStoreFiles.listingStored()
    }

    /**
     * Stored songs moved to the path [findSongs] gives their file now, so they keep their history: a song under a SAF
     * document URI that MediaStore lists, to its file path (the scanner before #370 stored every song so, and a walk stores
     * every file MediaStore skips so), and a song under a file path that MediaStore no longer lists (its folder took a
     * `.nomedia`, say) but an include tree still holds, to its document URI, which is the only way left to read it. Not
     * while MediaStore lists no file at all, which is it reindexing: the next import would only move them back. Also a
     * file MediaStore lists at another path under the same id. The listing read here is the one [findSongs] then takes,
     * read whole if [thorough] as a thorough import's own would be.
     */
    override suspend fun remapLegacySongs(
        existingSongs: List<Song>,
        thorough: Boolean
    ): List<SongPathRemap> {
        // Cleared first, so a findSongs after a remap that failed before listing reads a listing of its own
        remapListing = null
        val folders = folders()
        val primaryStoragePath = primaryStoragePath()
        val extraKeys = folders.extraTrees.map { tree -> treeKey(tree) }.toSet()
        val includeTrees = folders.includeTrees.filter { tree -> treeKey(tree) !in extraKeys }
        val documentSongs = existingSongs.filter { song -> legacyLocation(song.path) != null }
        val fileSongs = if (includeTrees.isEmpty()) emptyList() else existingSongs.filter { song -> song.path.startsWith("/") && folders.filter.accepts(song.path) }
        // Without MediaStore, none of the files are listed, so each one the trees hold is read through them
        val files = findAudioFiles(folders.filter, whole = thorough)
        remapListing = MediaStoreListing(folders.filter, files)
        val moves = movedSongRemaps(existingSongs, mediaStoreFiles.moved().filter { moved -> folders.filter.accepts(moved.file.path) })
        if (moves.isNotEmpty()) Timber.i("Matched ${moves.size} songs to files MediaStore lists at another path, by their id")
        if (documentSongs.isEmpty() && fileSongs.isEmpty()) return moves
        val toFiles = files?.let { LegacySafSongs(primaryStoragePath).remaps(documentSongs, files) }.orEmpty()
        val listedPaths = files?.mapTo(HashSet()) { file -> file.path.lowercase() }.orEmpty()
        val toDocuments =
            if (files?.isEmpty() == true) {
                emptyList()
            } else {
                documentRemaps(fileSongs.filter { song -> song.path.lowercase() !in listedPaths }, includeTrees, primaryStoragePath)
            }
        if (toFiles.isNotEmpty() || toDocuments.isNotEmpty()) {
            Timber.i("Matched ${toFiles.size} of ${documentSongs.size} songs stored under SAF document URIs to MediaStore files, and ${toDocuments.size} MediaStore no longer lists to their documents")
        }
        val remapped = (toFiles + toDocuments).map { remap -> remap.songId }.toSet()
        return toFiles + toDocuments + moves.filter { remap -> remap.songId !in remapped }
    }

    /** Of the stored [songs] under a file path, those whose document one of the include [trees] still holds, moved to its document URI. */
    private suspend fun documentRemaps(
        songs: List<Song>,
        trees: List<Uri>,
        primaryStoragePath: String
    ): List<SongPathRemap> = songs
        .mapNotNull { song -> includeDocument(song.path, trees, primaryStoragePath)?.let { document -> song to document } }
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { (song, document) ->
            // A song that can't be found or read keeps its path, for findSongs to look up again
            (SafDirectoryHelper.findDocument(context.contentResolver, document.uri) as? SafDirectoryHelper.DocumentLookup.Found)?.let { found ->
                SongPathRemap(songId = song.id, path = DocumentsContract.buildDocumentUriUsingTree(document.tree, found.node.documentId).toString())
            }
        }
        .mapNotNull { it }
        .toList()

    /**
     * The listing [remapLegacySongs] read for this import, if it was limited by [folderFilter] as this one is. Taken, so
     * the next import reads its own; null if there's none.
     */
    private fun takeRemapListing(folderFilter: FolderFilter): MediaStoreListing? = remapListing
        .also { remapListing = null }
        ?.takeIf { listing -> listing.filter == folderFilter }

    /**
     * The audio files MediaStore has indexed, on every volume, limited by [folderFilter]: only those that changed since the
     * last stored import read again, unless [whole]. Null if MediaStore can't be queried, for example without the audio
     * permission.
     */
    private suspend fun findAudioFiles(
        folderFilter: FolderFilter,
        whole: Boolean = false
    ): List<MediaStoreAudioFile>? = withContext(Dispatchers.IO) {
        mediaStoreFiles.list(whole)?.filter { file -> folderFilter.accepts(file.path) }
    }

    /**
     * Which of the include [trees] to walk: all of them if [thorough], or if MediaStore couldn't be listed
     * ([mediaStoreFiles] null), else those on a volume MediaStore indexes that it lists no file in, which looks like an
     * index that lost them (or never had them) rather than a folder with no music.
     */
    private fun includeTreesToWalk(
        trees: List<Uri>,
        mediaStoreFiles: List<MediaStoreAudioFile>?,
        thorough: Boolean,
        primaryStoragePath: String
    ): List<Uri> {
        if (thorough || mediaStoreFiles == null) return trees
        val mediaStoreVolumes = mediaStoreVolumes()
        return trees.filter { tree ->
            val folder = indexedTreeFolder(tree, primaryStoragePath, mediaStoreVolumes) ?: return@filter false
            val prefix = folder.trimEnd('/') + "/"
            mediaStoreFiles.none { file -> file.path.startsWith(prefix, ignoreCase = true) }
        }
    }

    /**
     * Walks [includeTrees] and the extra trees, leaving out the folders [ScannerFolders.filter] excludes, and notes the
     * playlist files each holds for [findPlaylists], those in excluded folders too.
     */
    private suspend fun walkTrees(
        includeTrees: List<Uri>,
        folders: ScannerFolders,
        primaryStoragePath: String
    ): TreeWalk = withContext(Dispatchers.IO) {
        val trees = includeTrees + folders.extraTrees
        if (trees.isEmpty()) return@withContext TreeWalk()
        val excludes = FolderFilter(excludes = folders.filter.excludes)
        // Read from the walks' concurrent collectors
        val excludedFolders: MutableList<DocumentNodeTree> = Collections.synchronizedList(mutableListOf())
        val statuses =
            trees
                .map { treeUri ->
                    // An excluded folder covers everything beneath it, so its songs aren't walked at all
                    SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, treeUri) { folder ->
                        val path = externalStorageTreeFolder(folder.uri.authority, folder.documentId, primaryStoragePath)
                        (path != null && !excludes.accepts("$path/")).also { excluded -> if (excluded) excludedFolders += folder }
                    }
                }
                .merge()
                .toList()
        val walked = statuses.filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>().map { status -> status.tree }
        // Excludes limit songs, not playlists, so the playlists in excluded folders are looked for there alone
        val excludedPlaylists =
            excludedFolders.toList().mapNotNull { folder ->
                SafDirectoryHelper.walkFolder(context.contentResolver, folder.rootUri, folder)
                    ?.let { tree -> treeKey(tree.rootUri) to tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
            }
        walkedPlaylistFiles =
            walked.associate { tree -> treeKey(tree.rootUri) to tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
                .let { found -> found + excludedPlaylists.filter { (key, _) -> key in found }.groupBy({ it.first }, { it.second }).mapValues { (key, lists) -> found.getValue(key) + lists.flatten() } }
        val extraKeys = folders.extraTrees.map { tree -> treeKey(tree) }.toSet()
        val (extraWalks, includeWalks) = walked.partition { tree -> treeKey(tree.rootUri) in extraKeys }
        val audioFiles = { tree: DocumentNodeTree -> tree.getLeaves().filter { node -> node.ext != "m3u" && node.ext != "m3u8" && node.ext != "pls" } }
        TreeWalk(
            includeFiles =
                includeWalks
                    .flatMap(audioFiles)
                    .mapNotNull { node ->
                        // A file in shared storage is the same song as MediaStore's row for it, so it's matched by its file path
                        val path = externalStorageTreeFolder(node.uri.authority, node.documentId, primaryStoragePath) ?: return@mapNotNull WalkedFile(node.uri.toString(), node)
                        WalkedFile(path, node.atSecondPrecision()).takeIf { excludes.accepts(path) }
                    }
                    .distinctBy { file -> file.key.lowercase() },
            extraDocuments =
                extraWalks
                    .flatMap(audioFiles)
                    .filter { node -> externalStorageTreeFolder(node.uri.authority, node.documentId, primaryStoragePath)?.let { path -> excludes.accepts(path) } ?: true }
                    .distinctBy { node -> node.uri },
            completeTrees = walked.map { tree -> treeKey(tree.rootUri) }.toSet(),
            unavailableExtraTrees =
                statuses.filterIsInstance<SafDirectoryHelper.TreeStatus.Unavailable>().map { status -> status.rootUri }.filter { tree -> treeKey(tree) in extraKeys }
        )
    }

    /**
     * The document a stored song's [path] names in one of the include [trees]: the document URI the song is stored under, or
     * a file path's document under the tree that holds it. Null if none of [trees] holds it.
     */
    private fun includeDocument(
        path: String,
        trees: List<Uri>,
        primaryStoragePath: String
    ): IncludeDocument? = trees.firstNotNullOfOrNull { tree ->
        if (path.startsWith("$tree/document/")) return@firstNotNullOfOrNull IncludeDocument(tree, Uri.parse(path), songFilePath(path, primaryStoragePath))
        if (!path.startsWith("/")) return@firstNotNullOfOrNull null
        val treeDocumentId =
            try {
                DocumentsContract.getTreeDocumentId(tree)
            } catch (e: IllegalArgumentException) {
                return@firstNotNullOfOrNull null
            }
        val treePath = externalStorageTreeFolder(tree.authority, treeDocumentId, primaryStoragePath) ?: return@firstNotNullOfOrNull null
        documentIdForPath(path, treeDocumentId, treePath)?.let { documentId -> IncludeDocument(tree, DocumentsContract.buildDocumentUriUsingTree(tree, documentId), path) }
    }

    /** The prefixes of the songs under [tree]: document URIs under it and, on shared storage, file paths in its folder. */
    private fun treeRoots(
        tree: Uri,
        primaryStoragePath: String
    ): List<String> = listOfNotNull("$tree/document/", treeFolder(tree, primaryStoragePath)?.let { it.trimEnd('/') + "/" })

    @Suppress("DEPRECATION")
    private fun primaryStoragePath(): String = Environment.getExternalStorageDirectory().path

    /** Songs read from SAF documents keep their document URI as their path, which the tag editor writes through. */
    private fun getExtraSongs(
        documents: List<DocumentNode>,
        merger: LocalFileTagMerger
    ): Flow<Song> = documents
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { node -> walkedSong(node.uri.toString(), node, merger, emptyList()) }
        .mapNotNull { it }

    /**
     * Songs an include tree's walk found that MediaStore didn't list, read through and stored under their document URI; or,
     * if [keepFilePaths], one stored under its file path stays there.
     */
    private fun getWalkedSongs(
        files: List<WalkedFile>,
        merger: LocalFileTagMerger,
        folderImageReader: FolderImageReader,
        keepFilePaths: Boolean
    ): Flow<Song> = files
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { file ->
            val path = file.key.takeIf { keepFilePaths && it.startsWith("/") && merger.existingSong(it) != null } ?: file.node.uri.toString()
            walkedSong(path, file.node, merger, folderImagesNear(file.key, folderImageReader))
        }
        .mapNotNull { it }

    /**
     * The stored [songs] MediaStore didn't list, each looked up by its document: still there, it's reused or read again like
     * a walked file; gone, it's left out; if its tree can't be read any more, it's left out and the tree added to
     * [lostTrees], whose songs the delete guard keeps; and if the lookup failed otherwise, it's kept as it was.
     */
    private fun getLookedUpSongs(
        songs: List<Pair<Song, IncludeDocument>>,
        merger: LocalFileTagMerger,
        folderImageReader: FolderImageReader,
        lostTrees: MutableSet<Uri>
    ): Flow<Song> = songs
        .asFlow()
        .concurrentMap((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1)) { (song, document) ->
            when (val lookup = SafDirectoryHelper.findDocument(context.contentResolver, document.uri)) {
                is SafDirectoryHelper.DocumentLookup.Found -> {
                    val node = if (document.filePath != null) lookup.node.atSecondPrecision() else lookup.node
                    walkedSong(song.path, node, merger, document.filePath?.let { path -> folderImagesNear(path, folderImageReader) }.orEmpty())
                }

                // The documents provider's word, unless the file can still be seen at its path
                SafDirectoryHelper.DocumentLookup.Missing -> song.copy(id = 0).takeIf { song.path.startsWith("/") && fileExists(song.path) }

                SafDirectoryHelper.DocumentLookup.Unreadable -> {
                    lostTrees += document.tree
                    null
                }

                SafDirectoryHelper.DocumentLookup.Unknown -> song.copy(id = 0)
            }
        }.mapNotNull { it }

    private suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).exists() }

    /** The images next to a song at [path] if it has a file path; the reader caches by folder, which the concurrent readers share. */
    private fun folderImagesNear(
        path: String,
        folderImageReader: FolderImageReader
    ): List<FolderImage> = if (path.startsWith("/")) synchronized(folderImageReader) { folderImageReader.imagesNear(path) } else emptyList()

    /** The song at [path] for a document a walk or lookup listed: reused if unchanged, else read through its document URI. */
    private suspend fun walkedSong(
        path: String,
        node: DocumentNode,
        merger: LocalFileTagMerger,
        folderImages: List<FolderImage>
    ): Song? = merger.unchangedSong(path, node.size, node.lastModified)?.reused(node.lastModified, folderImages)
        ?: tagReadGuard.read(TagReadFile(path, node.size, node.lastModified), type) { fileScanner.getAudioFile(context, kTagLib, node, path) }?.toSong(type, folderImages)
        // A file imported before that can't be read now keeps its song, which removing would take its play history with it
        ?: merger.existingSong(path)?.reused(node.lastModified, folderImages)

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

    private suspend fun readAudioFile(file: MediaStoreAudioFile): AudioFile? = tagReadGuard.read(TagReadFile(file.path, file.size, file.lastModified), type) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(file.contentUri, "r")?.use { pfd ->
                    kTagLib.getAudioFile(pfd.fd, file.path, file.displayName, file.lastModified, file.size, file.mimeType)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The native tag parse can throw anything for a corrupt file; one bad file shouldn't fail the whole import
                Timber.e(e, "Failed to read audio file: ${file.contentUri} (${file.path})")
                null
            }
        }
    }

    /**
     * The playlist files under each granted tree, then those MediaStore lists outside them. Trees on a volume MediaStore
     * indexes are found by one MediaStore query instead of walking every document in them; the trees the song scan just
     * walked reuse that walk, which finds those MediaStore skips too; any other tree (an extra tree, which MediaStore skips,
     * a volume it doesn't index such as a USB drive, a cloud provider) is walked here.
     */
    private suspend fun findPlaylistFiles(): List<PlaylistFile> = withContext(Dispatchers.IO) {
        val trees = grantedTrees()
        val primaryStoragePath = primaryStoragePath()
        val folders = folders()
        val extraTrees = folders.extraTrees.map { tree -> treeKey(tree) }.toSet()
        // Only a tree that is still an extra or include tree: one dropped since the walk is looked up like any other
        val scannedTrees = extraTrees + folders.includeTrees.map { tree -> treeKey(tree) }
        val walked = walkedPlaylistFiles.filterKeys { key -> key in scannedTrees }
        val mediaStoreVolumes = mediaStoreVolumes()
        val indexed = queryPlaylistFiles()
        // Trees with no file paths (Downloads, cloud storage) that couldn't be walked in full this time
        var pathlessTreeUnwalked = false
        val inTrees =
            trees.flatMap { tree ->
                val key = treeKey(tree)
                val folder = if (key in extraTrees) null else indexedTreeFolder(tree, primaryStoragePath, mediaStoreVolumes)
                // Playlists are only ever added, so one in a tree that can't be read now is kept as it was
                walked[key]
                    ?: folder?.let { indexed?.let { files -> playlistFilesIn(tree, folder, files) } }
                    ?: walkPlaylistFiles(tree)
                    ?: emptyList<PlaylistFile>().also { if (treeFolder(tree, primaryStoragePath) == null) pathlessTreeUnwalked = true }
            }.map { file -> file.copy(path = file.path ?: songFilePath(file.uri.toString(), primaryStoragePath)) }
        // The rest of what MediaStore lists, for a user who granted no folder (or not the one holding the playlist), but
        // not in the folders they excluded. A file a tree gave too has its id, which is its path, so it's one playlist read
        // through the tree; one in a tree with no paths is matched by name and size, and while such a tree can't be walked
        // none is added, as it may be one of that tree's
        val excludes = FolderFilter(excludes = folders.filter.excludes)
        val pathless = inTrees.filter { file -> file.path == null }
        val outsideTrees =
            if (pathlessTreeUnwalked) {
                emptyList()
            } else {
                indexed.orEmpty()
                    .filter { file -> excludes.accepts(file.path) && pathless.none { other -> other.displayName == file.displayName && other.size == file.size } }
                    .map { file -> PlaylistFile(file.uri, file.displayName, file.path, file.size) }
            }
        (inTrees + outsideTrees).distinctBy { file -> file.id.lowercase() }
    }

    /** The folder of [tree] if it has a file path (it's on a volume of the external storage provider), else null. */
    private fun treeFolder(
        tree: Uri,
        primaryStoragePath: String
    ): String? = try {
        externalStorageTreeFolder(tree.authority, DocumentsContract.getTreeDocumentId(tree), primaryStoragePath)
    } catch (e: IllegalArgumentException) {
        null
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
            arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.SIZE),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.m3u' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.m3u8'",
            null,
            null
        )?.use { cursor ->
            val files = mutableListOf<IndexedPlaylistFile>()
            while (cursor.moveToNext()) {
                val path = cursor.getString(0) ?: continue
                val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), cursor.getLong(2))
                files += IndexedPlaylistFile(path, cursor.getString(1) ?: path.substringAfterLast('/'), uri, if (cursor.isNull(3)) null else cursor.getLong(3))
            }
            files
        }
    } catch (e: SecurityException) {
        Timber.e(e, "Failed to query MediaStore for playlist files")
        null
    }

    /** The playlist files in [tree], or null if it couldn't be walked in full. */
    private suspend fun walkPlaylistFiles(tree: Uri): List<PlaylistFile>? = SafDirectoryHelper.buildFolderNodeTree(context.contentResolver, tree)
        .filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>()
        .map { complete -> complete.tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
        .toList()
        .takeIf { walks -> walks.isNotEmpty() }
        ?.flatten()

    override fun findPlaylists(existingSongs: List<Song>, knownVersions: Map<String, String>): Flow<FlowEvent<MediaImporter.PlaylistListing, MessageProgress>> = flow {
        val sanitisedSongPaths = M3uEntryMatcher.sanitisedPathsByFilename(existingSongs)

        val playlistFiles = findPlaylistFiles()
        val m3uPlaylists =
            playlistFiles
                .mapNotNull { file ->
                    try {
                        val folder = file.path?.substringBeforeLast('/', "")?.ifEmpty { null }
                        context.contentResolver.openInputStream(file.uri)
                            ?.use { inputStream -> inputStream.readBytes().decodeToString() }
                            ?.takeUnless(::isHlsIndex)
                            ?.let { text -> M3uParser().parse(path = file.id, fileName = file.displayName, text = text) }
                            ?.takeUnless { parsed -> allStreams(parsed.entries) }
                            // Entries relative to the file are resolved against its folder, so the matcher sees the path they name
                            ?.let { parsed -> parsed.copy(entries = parsed.entries.map { entry -> resolveEntry(entry, folder) }) }
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
        emit(FlowEvent.Success(MediaImporter.PlaylistListing(updates.toList())))
    }
}

/**
 * A playlist file, read through [uri]: a tree's document, or a MediaStore row outside every tree. [path] is its file path,
 * if it has one, and [id] what its playlist is stored under.
 */
private data class PlaylistFile(val uri: Uri, val displayName: String, val path: String? = null, val size: Long? = null) {
    val id: String get() = playlistFileId(uri, path)
}

/** MediaStore's audio [files] limited by [filter], or null [files] if it couldn't be queried. */
private class MediaStoreListing(val filter: FolderFilter, val files: List<MediaStoreAudioFile>?)

/**
 * An audio document an include tree's walk found, matched against MediaStore's listing and the stored songs by [key]: its
 * file path, or its document URI outside shared storage. Its song is stored under the document URI either way, unless
 * [TaglibMediaProvider.getWalkedSongs] keeps a stored file path while MediaStore lists nothing.
 */
private data class WalkedFile(val key: String, val node: DocumentNode)

/** The document [uri] under the include [tree] a stored song names, and the file path it has on shared storage ([filePath]). */
private data class IncludeDocument(val tree: Uri, val uri: Uri, val filePath: String?)

/** What [TaglibMediaProvider]'s walk of the include and extra trees found, and which trees it read in full ([completeTrees], by [treeKey]). */
private data class TreeWalk(
    val includeFiles: List<WalkedFile> = emptyList(),
    val extraDocuments: List<DocumentNode> = emptyList(),
    val completeTrees: Set<String> = emptySet(),
    val unavailableExtraTrees: List<Uri> = emptyList()
)

/**
 * [this] with its modified date cut to whole seconds, which is all MediaStore keeps: a shared storage file's song moves
 * between MediaStore's listing and a walk's (MediaStore stops or starts listing it, and the song's path moves with it), so
 * both must give the same date for an unchanged file.
 */
private fun DocumentNode.atSecondPrecision() = DocumentNode(uri, documentId, displayName, mimeType, lastModified = lastModified / 1000 * 1000, size = size)

private data class IndexedPlaylistFile(val path: String, val displayName: String, val uri: Uri, val size: Long?)

/** Whether [location] names a place on its own (a path from the root, a drive, a URL) rather than relative to the playlist file. */
private fun isAbsoluteLocation(location: String) = location.startsWith("/") || location.startsWith("\\") || "://" in location || Regex("^[A-Za-z]:").containsMatchIn(location)

/** [entry] with a relative location put under the folder [playlistFolder] of the playlist that holds it, as players resolve it. */
internal fun resolveEntry(
    entry: Entry,
    playlistFolder: String?
): Entry = if (playlistFolder == null || isAbsoluteLocation(entry.location)) {
    entry
} else {
    Entry(
        location = "${playlistFolder.trimEnd('/')}/${entry.location}",
        duration = entry.duration,
        artist = entry.artist,
        track = entry.track,
        rawLines = entry.rawLines
    )
}

private fun DocumentNode.isPlaylist() = ext == "m3u" || ext == "m3u8"

private fun DocumentNode.toPlaylistFile() = PlaylistFile(uri, displayName, size = size)

/** Whether [text] is an HLS stream's index (an `.m3u8` a streaming app left behind), which names no songs. */
private fun isHlsIndex(text: String) = "#EXT-X-" in text

private val streamSchemes = setOf("http", "https", "rtsp", "rtmp", "rtmps", "mms", "mmsh", "icy", "ftp")

/** Whether every one of [entries] names a network stream, none a file or document a song could be at (`file://`, `content://`). */
private fun allStreams(entries: List<Entry>) = entries.isNotEmpty() && entries.all { entry -> entry.location.substringBefore("://", "").lowercase() in streamSchemes }

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
            PlaylistFile(DocumentsContract.buildDocumentUriUsingTree(tree, documentId), file.displayName, file.path, file.size)
        }
}
