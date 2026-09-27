package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Writes playlists back to the .m3u files `TaglibMediaProvider.findPlaylists` imported them from, through their SAF document URIs. */
class SafPlaylistFileSync(
    private val context: Context,
    private val songDataDao: SongDataDao
) : PlaylistFileSync {
    private val m3uWriter = M3uWriter()
    private val m3uParser = M3uParser()

    override suspend fun write(
        playlist: Playlist,
        songs: List<Song>
    ) {
        val uri = Uri.parse(playlist.externalId)
        withContext(Dispatchers.IO) {
            try {
                val preservedEntries = readPreservedEntries(uri, songs)
                val outputStream = context.contentResolver.openOutputStream(uri, "wt")
                if (outputStream == null) {
                    Timber.w("Could not open output stream to sync m3u file for playlist '${playlist.name}' at $uri")
                    return@withContext
                }
                outputStream.use { it.write(m3uWriter.write(songs, preservedEntries).toByteArray(Charsets.UTF_8)) }
            } catch (e: IOException) {
                Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri")
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to sync m3u file for playlist '${playlist.name}' at $uri (permission denied)")
            }
        }
    }

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
