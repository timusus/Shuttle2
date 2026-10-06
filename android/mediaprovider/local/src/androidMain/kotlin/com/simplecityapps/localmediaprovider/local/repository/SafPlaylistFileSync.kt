package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.provider.taglib.currentPlaylistFileId
import com.simplecityapps.localmediaprovider.local.provider.taglib.externalStorageTreeFolder
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.storage.documentIdForPath
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Writes playlists back to the .m3u files `TaglibMediaProvider.findPlaylists` imported them from: one known by its file
 * path through a granted folder that holds it, or else the file itself; one with no file path through its document URI.
 * A playlist whose file couldn't be written is noted in [unwritten] until one is.
 */
class SafPlaylistFileSync(
    private val context: Context,
    private val songDataDao: SongDataDao
) : PlaylistFileSync {
    private val m3uWriter = M3uWriter()
    private val m3uParser = M3uParser()

    // The ids of the playlists holding edits their file couldn't take
    private val unwritten by lazy { context.getSharedPreferences("unwritten_playlist_files", Context.MODE_PRIVATE) }

    override suspend fun write(
        playlist: Playlist,
        songs: List<Song>
    ) {
        val externalId = playlist.externalId ?: return
        withContext(Dispatchers.IO) {
            val uri = target(externalId)
            val written =
                try {
                    val preservedEntries = readPreservedEntries(uri, songs)
                    val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                    if (outputStream == null) {
                        Timber.w("Could not open output stream to sync m3u file for playlist '${playlist.name}' at $uri")
                        false
                    } else {
                        outputStream.use { it.write(m3uWriter.write(songs, preservedEntries).toByteArray(Charsets.UTF_8)) }
                        true
                    }
                } catch (e: IOException) {
                    Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri")
                    false
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri (permission denied)")
                    false
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri")
                    false
                }
            unwritten.edit().apply { if (written) remove(externalId) else putBoolean(externalId, true) }.apply()
        }
    }

    override fun holdsUnwrittenEdits(externalId: String): Boolean = unwritten.getBoolean(externalId, false)

    override fun currentId(externalId: String): String = currentPlaylistFileId(externalId, primaryStoragePath())

    /**
     * Where the playlist file [externalId] names is written: a file path's document in a folder granted with write access,
     * else the file itself (writable without a grant only before scoped storage); any other id is the document's URI.
     */
    private fun target(externalId: String): Uri {
        val uri = Uri.parse(externalId)
        val path = uri.path?.takeIf { uri.scheme == "file" } ?: return uri
        val primaryStoragePath = primaryStoragePath()
        return context.contentResolver.persistedUriPermissions
            .filter { permission -> permission.isWritePermission }
            .firstNotNullOfOrNull { permission ->
                val tree = permission.uri
                try {
                    val treeDocumentId = DocumentsContract.getTreeDocumentId(tree)
                    externalStorageTreeFolder(tree.authority, treeDocumentId, primaryStoragePath)
                        ?.let { folder -> documentIdForPath(path, treeDocumentId, folder) }
                        ?.let { documentId -> DocumentsContract.buildDocumentUriUsingTree(tree, documentId) }
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            ?: uri
    }

    @Suppress("DEPRECATION")
    private fun primaryStoragePath(): String = Environment.getExternalStorageDirectory().path

    /**
     * Groups entries from the existing m3u file that don't resolve to any library song (moved,
     * unscanned, or a remote URL the importer never turned into a [PlaylistSong]) by the nearest
     * preceding entry that resolves to a song still in [songs], so [M3uWriter] can reinsert them
     * at roughly their original position instead of silently dropping them on rewrite. An entry
     * whose resolved song is no longer in [songs] (removed from the playlist, or since blacklisted)
     * doesn't itself update the anchor, so later unresolved entries cascade back to the previous
     * surviving anchor. Returns an empty map if the file can no longer be read (moved, permission
     * revoked) - the rewrite then reflects only the resolved songs, as before this fix.
     */
    private suspend fun readPreservedEntries(
        uri: Uri,
        songs: List<Song>
    ): Map<Long?, List<Entry>> {
        val entries =
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    m3uParser.parse(path = uri.toString(), fileName = uri.lastPathSegment.orEmpty(), text = inputStream.readBytes().decodeToString()).entries
                }
            } catch (e: IOException) {
                Timber.w(e, "Could not read existing m3u file to preserve unresolved entries at $uri")
                null
            } catch (e: SecurityException) {
                Timber.w(e, "Could not read existing m3u file to preserve unresolved entries at $uri (permission denied)")
                null
            } ?: return emptyMap()

        val sanitisedSongPaths = M3uEntryMatcher.sanitisedPathsByFilename(songDataDao.get().map { it.toSong() })
        val songIdsInPlaylist = songs.map { it.id }.toSet()

        val preservedEntriesByAnchor = mutableMapOf<Long?, MutableList<Entry>>()
        var anchor: Long? = null
        entries.forEach { entry ->
            val matchedSong = M3uEntryMatcher.match(entry, sanitisedSongPaths)
            if (matchedSong != null) {
                if (matchedSong.id in songIdsInPlaylist) {
                    anchor = matchedSong.id
                }
            } else {
                preservedEntriesByAnchor.getOrPut(anchor) { mutableListOf() }.add(entry)
            }
        }
        return preservedEntriesByAnchor
    }
}
