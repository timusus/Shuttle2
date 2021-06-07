package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.R
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.PlaylistSong
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalPlaylistRepository(
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao
) : PlaylistRepository {

    private val playlistsRelay: StateFlow<List<Playlist>?> by lazy {
        playlistDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> {
        return playlistsRelay
            .filterNotNull()
            .map { playlists ->
                playlists
                    .filter(query.predicate)
                    .toMutableList()
                    .sortedWith(query.sortOrder.comparator)
            }
    }

    override fun getSmartPlaylists(): Flow<List<SmartPlaylist>> {
        return flow {
            emit(
                listOf(
                    SmartPlaylist(R.string.playlist_title_recently_added, SongQuery.RecentlyAdded()),
                    SmartPlaylist(R.string.playlist_title_most_played, SongQuery.PlayCount(2, SongSortOrder.PlayCount))
                )
            )
        }
    }

    override suspend fun getFavoritesPlaylist(): Playlist {
        return withContext(Dispatchers.IO) {
            playlistsRelay
                .filterNotNull()
                .firstOrNull()
                ?.firstOrNull { it.name == "Favorites" } ?: createPlaylist("Favorites", null, null)
        }
    }

    override suspend fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Playlist {
        val playlistId = playlistDataDao.insert(
            PlaylistData(
                name = name,
                mediaStoreId = mediaStoreId,
                sortOrder = PlaylistSongSortOrder.Position
            )
        )
        playlistSongJoinDao.insert(songs.orEmpty().map { song -> PlaylistSongJoin(playlistId, song.id) })
        val playlist = playlistDataDao.getPlaylist(playlistId)
        Timber.v("Created playlist: ${playlist.name} with ${playlist.songCount} songs}")
        return playlist
    }

    override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        return playlistSongJoinDao.insert(songs.map { song ->
            PlaylistSongJoin(
                playlistId = playlist.id,
                songId = song.id
            )
        })
    }

    override suspend fun removeFromPlaylist(playlist: Playlist, playlistSongs: List<PlaylistSong>) {
        return playlistSongJoinDao.delete(
            playlistId = playlist.id,
            playlistSongIds = playlistSongs.map { playlistSong -> playlistSong.id }.toTypedArray()
        )
    }

    override suspend fun removeSongsFromPlaylist(playlist: Playlist, songs: List<Song>) {
        return playlistSongJoinDao.deleteSongs(
            playlistId = playlist.id,
            songIds = songs.map { it.id }.toTypedArray()
        )
    }

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> {
        return playlistSongJoinDao.getSongsForPlaylist(playlist.id)
            .map { playlistSong ->
                playlistSong.sortedWith(playlist.sortOrder.comparator)
            }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        return playlistDataDao.delete(playlist.id)
    }

    override suspend fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?) {
        return playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                mediaStoreId = mediaStoreId,
                sortOrder = playlist.sortOrder
            )
        )
    }

    override suspend fun clearPlaylist(playlist: Playlist) {
        return playlistDataDao.clear(playlist.id)
    }

    override suspend fun renamePlaylist(playlist: Playlist, name: String) {
        return playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = name,
                mediaStoreId = playlist.mediaStoreId,
                sortOrder = playlist.sortOrder
            )
        )
    }

    override suspend fun updatePlaylistSortOder(playlist: Playlist, sortOrder: PlaylistSongSortOrder) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                mediaStoreId = playlist.mediaStoreId,
                sortOrder = sortOrder
            )
        )
    }

    override suspend fun updatePlaylistSongsSortOder(playlist: Playlist, playlistSongs: List<PlaylistSong>) {
        playlistSongJoinDao.updateSortOrder(playlistSongs.map { playlistSong ->
            PlaylistSongJoin(playlist.id, playlistSong.song.id).apply {
                id = playlistSong.id
                sortOrder = playlistSong.sortOrder
            }
        })
    }
}