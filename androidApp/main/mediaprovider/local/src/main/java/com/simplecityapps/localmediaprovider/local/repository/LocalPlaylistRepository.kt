package com.simplecityapps.localmediaprovider.local.repository

import android.content.Context
import com.simplecityapps.localmediaprovider.R
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistDataDao
import com.simplecityapps.localmediaprovider.local.data.room.dao.PlaylistSongJoinDao
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistData
import com.simplecityapps.localmediaprovider.local.data.room.entity.PlaylistSongJoin
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.playlists.comparator
import com.simplecityapps.shuttle.model.*
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import timber.log.Timber

class LocalPlaylistRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val playlistDataDao: PlaylistDataDao,
    private val playlistSongJoinDao: PlaylistSongJoinDao
) : PlaylistRepository {

    private val playlistsRelay: StateFlow<List<Playlist>?> by lazy {
        playlistDataDao
            .getAll()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Lazily, null)
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
        val favoritesName = context.getString(R.string.playlist_title_favorites)
        return withContext(Dispatchers.IO) {
            playlistsRelay
                .filterNotNull()
                .firstOrNull()
                ?.firstOrNull { it.name == favoritesName }
                ?: createPlaylist(
                    name = favoritesName,
                    mediaProviderType = MediaProviderType.Shuttle,
                    songs = null,
                    externalId = null
                )
        }
    }

    override suspend fun createPlaylist(name: String, mediaProviderType: MediaProviderType, songs: List<Song>?, externalId: String?): Playlist {
        val playlistId = playlistDataDao.insert(
            PlaylistData(
                name = name,
                sortOrder = PlaylistSongSortOrder.Position,
                mediaProviderType = mediaProviderType,
                externalId = externalId
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

    override suspend fun clearPlaylist(playlist: Playlist) {
        return playlistDataDao.clear(playlist.id)
    }

    override suspend fun renamePlaylist(playlist: Playlist, name: String) {
        return playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = name,
                externalId = playlist.externalId,
                sortOrder = playlist.sortOrder
            )
        )
    }

    override suspend fun updatePlaylistSortOder(playlist: Playlist, sortOrder: PlaylistSongSortOrder) {
        playlistDataDao.update(
            PlaylistData(
                id = playlist.id,
                name = playlist.name,
                externalId = playlist.externalId,
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