package com.simplecityapps.localmediaprovider.local.repository

import com.simplecityapps.localmediaprovider.R
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.SongQuery
import com.simplecityapps.mediaprovider.repository.SongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalPlaylistRepository(
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao
) : PlaylistRepository {

    private val playlistsRelay: Flow<List<Playlist>> by lazy {
        ConflatedBroadcastChannel<List<Playlist>?>(null)
            .apply {
                CoroutineScope(Dispatchers.IO)
                    .launch {
                        playlistDataDao
                            .getAll()
                            .collect { playlists ->
                                send(playlists)
                            }
                    }
            }
            .asFlow()
            .flowOn(Dispatchers.IO)
            .filterNotNull()
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> {
        return playlistsRelay.map { playlists ->
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
            playlistsRelay.firstOrNull()?.firstOrNull { it.name == "Favorites" } ?: createPlaylist("Favorites", null, null)
        }
    }

    override suspend fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Playlist {
        val playlistId = playlistDataDao.insert(PlaylistData(name = name, mediaStoreId = mediaStoreId))
        playlistSongJoinDao.insert(songs.orEmpty().map { song -> PlaylistSongJoin(playlistId, song.id) })
        val playlist = playlistDataDao.getPlaylist(playlistId)
        Timber.v("Created playlist: ${playlist.name} with ${playlist.songCount} songs}")
        return playlist
    }

    override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        return playlistSongJoinDao.insert(songs.map { song -> PlaylistSongJoin(playlist.id, song.id) })
    }

    override suspend fun removeFromPlaylist(playlist: Playlist, songs: List<Song>) {
        return playlistSongJoinDao.delete(playlist.id, songs.map { song -> song.id }.toTypedArray())
    }

    override fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return playlistSongJoinDao.getSongsForPlaylist(playlistId)
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        return playlistDataDao.delete(
            PlaylistData(playlist.id, playlist.name)
        )
    }

    override suspend fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?) {
        return playlistDataDao.update(PlaylistData(playlist.id, playlist.name, mediaStoreId))
    }

    override suspend fun clearPlaylist(playlist: Playlist) {
        return playlistDataDao.delete(playlist.id)
    }
}