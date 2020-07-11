package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Playlist
    suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>)
    suspend fun removeFromPlaylist(playlist: Playlist, songs: List<Song>)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?)
    suspend fun clearPlaylist(playlist: Playlist)
}

sealed class PlaylistQuery {
    class PlaylistId(val playlistId: Long) : PlaylistQuery()
    class PlaylistName(val name: String) : PlaylistQuery()
}

fun PlaylistQuery.predicate(): (Playlist) -> Boolean {
    return when (this) {
        is PlaylistQuery.PlaylistId -> { playlist -> playlist.id == playlistId }
        is PlaylistQuery.PlaylistName -> { playlist -> playlist.name == name }
    }
}