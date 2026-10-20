package com.simplecityapps.localmediaprovider.local.provider.taglib

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.PlaylistListing
import com.simplecityapps.mediaprovider.PlaylistUpdateData
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.saf.DocumentNode
import com.simplecityapps.saf.SafDirectoryHelper
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The playlist files in the granted trees and MediaStore, matched to the stored songs. */
internal class TaglibPlaylists(
    private val context: Context,
    private val type: MediaProviderType,
    private val grantedTrees: () -> List<Uri>,
    private val folders: () -> ScannerFolders,
    private val ioDispatcher: CoroutineDispatcher,
    private val saf: SafDirectoryHelper,
    // The playlist files the last song walk found in each tree it walked, by [treeKey]
    private val walkedPlaylistFiles: () -> Map<String, List<PlaylistFile>>
) {
    /**
     * The playlist files under each granted tree, then those MediaStore lists outside them. Trees on a volume MediaStore
     * indexes are found by one MediaStore query instead of walking every document in them; the trees the song scan just
     * walked reuse that walk, which finds those MediaStore skips too; any other tree (an extra tree, which MediaStore skips,
     * a volume it doesn't index such as a USB drive, a cloud provider) is walked here.
     */
    private suspend fun findPlaylistFiles(): List<PlaylistFile> = withContext(ioDispatcher) {
        val trees = grantedTrees()
        val primaryStoragePath = primaryStoragePath()
        val folders = folders()
        val extraTrees = folders.extraTrees.map { tree -> treeKey(tree) }.toSet()
        // Only a tree that is still an extra or include tree: one dropped since the walk is looked up like any other
        val scannedTrees = extraTrees + folders.includeTrees.map { tree -> treeKey(tree) }
        val walked = walkedPlaylistFiles().filterKeys { key -> key in scannedTrees }
        val mediaStoreVolumes = mediaStoreVolumes(context)
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
    private suspend fun walkPlaylistFiles(tree: Uri): List<PlaylistFile>? = saf.buildFolderNodeTree(context.contentResolver, tree)
        .filterIsInstance<SafDirectoryHelper.TreeStatus.Complete>()
        .map { complete -> complete.tree.getLeaves().filter { it.isPlaylist() }.map { it.toPlaylistFile() } }
        .toList()
        .takeIf { walks -> walks.isNotEmpty() }
        ?.flatten()

    fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<PlaylistListing, MessageProgress>> = flow {
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
                        PlaylistUpdateData(
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
        emit(FlowEvent.Success(PlaylistListing(updates.toList())))
    }
}

/**
 * A playlist file, read through [uri]: a tree's document, or a MediaStore row outside every tree. [path] is its file path,
 * if it has one, and [id] what its playlist is stored under.
 */
internal data class PlaylistFile(val uri: Uri, val displayName: String, val path: String? = null, val size: Long? = null) {
    val id: String get() = playlistFileId(uri, path)
}

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

internal fun DocumentNode.isPlaylist() = ext == "m3u" || ext == "m3u8"

internal fun DocumentNode.toPlaylistFile() = PlaylistFile(uri, displayName, size = size)

/** Whether [text] is an HLS stream's index (an `.m3u8` a streaming app left behind), which names no songs. */
private fun isHlsIndex(text: String) = "#EXT-X-" in text

private val streamSchemes = setOf("http", "https", "rtsp", "rtmp", "rtmps", "mms", "mmsh", "icy", "ftp")

/** Whether every one of [entries] names a network stream, none a file or document a song could be at (`file://`, `content://`). */
private fun allStreams(entries: List<Entry>) = entries.isNotEmpty() && entries.all { entry -> entry.location.substringBefore("://", "").lowercase() in streamSchemes }

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
