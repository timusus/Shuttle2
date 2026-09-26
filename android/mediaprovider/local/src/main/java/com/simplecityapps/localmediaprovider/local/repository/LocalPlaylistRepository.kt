package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import android.net.Uri
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.toSong
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.ImportedPlaylistStore
import com.simplecityapps.mediaprovider.M3uEntryMatcher
import com.simplecityapps.mediaprovider.M3uParser
import com.simplecityapps.mediaprovider.M3uWriter
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * True for playlists imported from a local .m3u file (their [Playlist.externalId] is the file's
 * SAF document URI, set by `TaglibMediaProvider.findPlaylists`) - the only ones with a file to
 * keep in sync when their songs change.
 */
internal fun Playlist.isM3uSynced(): Boolean = isM3uSynced(mediaProvider, externalId)

private fun isM3uSynced(
    mediaProvider: MediaProviderType,
    externalId: String?
): Boolean = mediaProvider == MediaProviderType.Shuttle && externalId != null

class LocalPlaylistRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao,
    private val songDataDao: SongDataDao
) : PlaylistRepository,
    ImportedPlaylistStore {
    private val m3uWriter = M3uWriter()
    private val m3uParser = M3uParser()

    private val playlistsRelay: StateFlow<List<Playlist>?> by lazy {
        playlistDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> = playlistsRelay
        .filterNotNull()
        .map { playlists ->
            playlists
                .filter(query.predicate)
                .toMutableList()
                .sortedWith(query.sortOrder.comparator)
        }

    override suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist {
        val playlistId =
            playlistDataDao.insert(
                PlaylistData(
                    name = name,
                    sortOrder = PlaylistSongSortOrder.Position,
                    mediaProviderType = mediaProviderType,
                    externalId = externalId
                ),
                songIds = songs.orEmpty().inLibrary().map { song -> song.id }
            )
        val playlist = playlistDataDao.getPlaylist(playlistId)
        Timber.v("Created playlist: ${playlist.name} with ${playlist.songCount} songs}")
        return playlist
    }

    /**
     * Doesn't write the playlist back to its m3u file: it has just been read from it. That file already holds the edits made to
     * the playlist in S2 ([syncM3uFile]), so the playlist is given exactly its songs; a media server's playlist never hears of
     * them, so it keeps the songs added in S2 and gains the server's new ones.
     */
    override suspend fun storePlaylist(playlist: MediaImporter.PlaylistUpdateData) = withContext(Dispatchers.IO) {
        playlistDataDao.storeImported(
            PlaylistData(
                name = playlist.name,
                sortOrder = PlaylistSongSortOrder.Position,
                mediaProviderType = playlist.mediaProviderType,
                externalId = playlist.externalId
            ),
            songIds = playlist.songs.inLibrary().map { song -> song.id },
            replaceSongs = isM3uSynced(playlist.mediaProviderType, playlist.externalId)
        )
    }

    override suspend fun addToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.insert(
            songs.inLibrary().mapIndexed { i, song ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = song.id,
                    sortOrder = (playlist.songCount + i).toLong()
                )
            }
        )
        syncM3uFile(playlist)
    }

    override suspend fun removeFromPlaylist(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.delete(
            playlistId = playlist.id,
            playlistSongIds = playlistSongs.map { playlistSong -> playlistSong.id }.toTypedArray()
        )
        syncM3uFile(playlist)
    }

    override suspend fun removeSongsFromPlaylist(
        playlist: Playlist,
        songs: List<Song>
    ) {
        playlistSongJoinDao.deleteSongs(
            playlistId = playlist.id,
            songIds = songs.map { it.id }.toTypedArray()
        )
        syncM3uFile(playlist)
    }

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> = playlistSongJoinDao.getSongsForPlaylist(playlist.id)
        .map { playlistSongs -> playlistSongs.sortedForPlaylist(playlist) }

    /**
     * The DAO's grouping query returns one representative song per album, in no useful order - so the playlist's
     * own sort is applied here, the same way [getSongsForPlaylist] applies it, before taking [limit].
     */
    override fun getPlaylistCoverSongs(playlist: Playlist, limit: Int): Flow<List<Song>> = playlistSongJoinDao.getCoverSongsForPlaylist(playlist.id)
        .map { playlistSongs -> playlistSongs.sortedForPlaylist(playlist).take(limit).map { it.song } }

    override suspend fun deletePlaylist(playlist: Playlist) = playlistDataDao.delete(playlist.id)

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) = playlistDataDao.deleteAll(mediaProviderType)

    override suspend fun clearPlaylist(playlist: Playlist) {
        playlistDataDao.clear(playlist.id)
        syncM3uFile(playlist)
    }

    override suspend fun renamePlaylist(
        playlist: Playlist,
        name: String
    ) = playlistDataDao.update(
        PlaylistData(
            id = playlist.id,
            name = name,
            externalId = playlist.externalId,
            mediaProviderType = playlist.mediaProvider,
            sortOrder = playlist.sortOrder,
            sortDescending = playlist.sortDescending
        )
    )

    override suspend fun updatePlaylistSortOder(
        playlist: Playlist,
        sortOrder: PlaylistSongSortOrder,
        sortDescending: Boolean
    ) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                externalId = playlist.externalId,
                mediaProviderType = playlist.mediaProvider,
                sortOrder = sortOrder,
                sortDescending = sortDescending
            )
        )
    }

    override suspend fun updatePlaylistSongsSortOder(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    ) {
        playlistSongJoinDao.updateSortOrder(
            playlistSongs.map { playlistSong ->
                PlaylistSongJoin(
                    playlistId = playlist.id,
                    songId = playlistSong.song.id,
                    sortOrder = playlistSong.sortOrder
                ).apply {
                    id = playlistSong.id
                }
            }
        )
        syncM3uFile(playlist)
    }

    /**
     * Rewrites the source .m3u file for an m3u-imported [playlist] after its songs change, so an
     * external player sees the same edit. Best-effort: the file may have moved or lost its SAF
     * grant since import, so failures are logged rather than surfaced - the in-app playlist is
     * still the source of truth.
     */
    private suspend fun syncM3uFile(playlist: Playlist) {
        if (!playlist.isM3uSynced()) {
            return
        }
        val uri = Uri.parse(playlist.externalId)
        withContext(Dispatchers.IO) {
            try {
                val songs = getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
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
                    m3uParser.parse(path = uri.toString(), fileName = uri.lastPathSegment.orEmpty(), inputStream = inputStream).entries
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

/**
 * The songs that can be saved to a playlist. A file opened from another app that isn't in the library (e.g. from
 * the queue) has no song row for a playlist to refer to, so it's left out rather than failing the whole write.
 */
private fun List<Song>.inLibrary(): List<Song> = filter { song -> song.isInLibrary }

private fun List<PlaylistSong>.sortedForPlaylist(playlist: Playlist): List<PlaylistSong> {
    val comparator = playlist.sortOrder.comparator
    return sortedWith(if (playlist.sortDescending) comparator.reversed() else comparator)
}
