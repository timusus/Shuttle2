package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.ImportedPlaylistStore
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

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
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao,
    private val fileSync: PlaylistFileSync,
    private val albumIndex: LibraryAlbumIndex
) : PlaylistRepository,
    ImportedPlaylistStore {
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
        logger.debug { "Created playlist: ${playlist.name} with ${playlist.songCount} songs}" }
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
        .withAlbumIdentities()
        .map { playlistSongs -> playlistSongs.sortedForPlaylist(playlist) }

    /**
     * The DAO's grouping query returns one representative song per album, in no useful order - so the playlist's
     * own sort is applied here, the same way [getSongsForPlaylist] applies it, then one song per album identity is kept
     * before taking [limit].
     */
    override fun getPlaylistCoverSongs(playlist: Playlist, limit: Int): Flow<List<Song>> = playlistSongJoinDao.getCoverSongsForPlaylist(playlist.id)
        .withAlbumIdentities()
        .map { playlistSongs -> playlistSongs.sortedForPlaylist(playlist).map { it.song }.distinctBy { it.resolvedAlbumIdentity.groupKey }.take(limit) }

    /** Each song holding its album identity, from the library's index as it is when the songs are read. */
    private fun Flow<List<PlaylistSong>>.withAlbumIdentities(): Flow<List<PlaylistSong>> = combine(this, albumIndex.updates) { entries, index ->
        entries.map { entry -> index.identities[entry.song.id]?.let { entry.copy(song = entry.song.copy(albumIdentity = it)) } ?: entry }
    }

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

    /** Writes an m3u-imported [playlist]'s songs back to its file after they change; see [PlaylistFileSync]. */
    private suspend fun syncM3uFile(playlist: Playlist) {
        if (!playlist.isM3uSynced()) {
            return
        }
        fileSync.write(playlist, getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song })
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

private val logger = Logger.tagged("LocalPlaylistRepository")
