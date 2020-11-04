package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.SmartPlaylist
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

interface PlaylistRepository {
    fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>>
    suspend fun getFavoritesPlaylist(): Playlist
    suspend fun createPlaylist(name: String, mediaStoreId: Long?, songs: List<Song>?): Playlist
    suspend fun addToPlaylist(playlist: Playlist, songs: List<Song>)
    suspend fun removeFromPlaylist(playlist: Playlist, songs: List<Song>)
    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>>
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun updatePlaylistMediaStoreId(playlist: Playlist, mediaStoreId: Long?)
    suspend fun clearPlaylist(playlist: Playlist)
    fun getSmartPlaylists(): Flow<List<SmartPlaylist>>
}

sealed class PlaylistQuery(
    val predicate: ((Playlist) -> Boolean),
    val sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default
) {
    class All(sortOrder: PlaylistSortOrder = PlaylistSortOrder.Default) : PlaylistQuery(
        predicate = { true },
        sortOrder = sortOrder
    )

    class PlaylistId(val playlistId: Long) : PlaylistQuery(
        predicate = { playlist -> playlist.id == playlistId },
        sortOrder = PlaylistSortOrder.Default
    )
}

enum class PlaylistSortOrder : Serializable {
    Default;

    val comparator: Comparator<Playlist>
        get() {
            return when (this) {
                Default -> compareBy { playlist -> playlist.id }
            }
        }
}