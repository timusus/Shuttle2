package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakePlaylistRepository : PlaylistRepository {
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val smartPlaylists = MutableStateFlow<List<SmartPlaylist>>(emptyList())

    fun setPlaylists(value: List<Playlist>) {
        playlists.value = value
    }

    fun setSmartPlaylists(value: List<SmartPlaylist>) {
        smartPlaylists.value = value
    }

    override fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>> = playlists

    override fun getSmartPlaylists(): Flow<List<SmartPlaylist>> = smartPlaylists

    override suspend fun getFavoritesPlaylist(): Playlist = error("Not implemented")

    override suspend fun createPlaylist(name: String, mediaProviderType: MediaProviderType, songs: List<Song>?, externalId: String?): Playlist = error("Not implemented")

    override suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>) {}

    override suspend fun removeFromPlaylist(playlist: Playlist, playlistSongs: List<PlaylistSong>) {}

    override suspend fun removeSongsFromPlaylist(playlist: Playlist, songs: List<Song>) {}

    override fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>> = MutableStateFlow(emptyList())

    override suspend fun deletePlaylist(playlist: Playlist) {}

    override suspend fun deleteAll(mediaProviderType: MediaProviderType) {}

    override suspend fun clearPlaylist(playlist: Playlist) {}

    override suspend fun renamePlaylist(playlist: Playlist, name: String) {}

    override suspend fun updatePlaylistSortOder(playlist: Playlist, sortOrder: PlaylistSongSortOrder) {}

    override suspend fun updatePlaylistSongsSortOder(playlist: Playlist, playlistSongs: List<PlaylistSong>) {}

    override suspend fun updatePlaylistMediaProviderType(playlist: Playlist, mediaProviderType: MediaProviderType) {}

    override suspend fun updatePlaylistExternalId(playlist: Playlist, externalId: String?) {}
}
