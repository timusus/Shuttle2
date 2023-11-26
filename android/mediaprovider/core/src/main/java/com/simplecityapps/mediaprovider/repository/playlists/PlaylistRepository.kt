package com.simplecityapps.mediaprovider.repository.playlists

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.PlaylistSong
import com.simplecityapps.shuttle.model.SmartPlaylist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
import java.io.Serializable
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylists(query: PlaylistQuery): Flow<List<Playlist>>

    suspend fun getFavoritesPlaylist(): Playlist

    suspend fun createPlaylist(
        name: String,
        mediaProviderType: MediaProviderType,
        songs: List<Song>?,
        externalId: String?
    ): Playlist

    suspend fun addToPlaylist(
        playlist: Playlist,
        songs: List<Song>
    )

    suspend fun removeFromPlaylist(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    )

    suspend fun removeSongsFromPlaylist(
        playlist: Playlist,
        songs: List<Song>
    )

    fun getSongsForPlaylist(playlist: Playlist): Flow<List<PlaylistSong>>

    suspend fun deletePlaylist(playlist: Playlist)

    suspend fun deleteAll(mediaProviderType: MediaProviderType)

    suspend fun clearPlaylist(playlist: Playlist)

    suspend fun renamePlaylist(
        playlist: Playlist,
        name: String
    )

    fun getSmartPlaylists(): Flow<List<SmartPlaylist>>

    suspend fun updatePlaylistSortOder(
        playlist: Playlist,
        sortOrder: PlaylistSongSortOrder
    )

    suspend fun updatePlaylistSongsSortOder(
        playlist: Playlist,
        playlistSongs: List<PlaylistSong>
    )

    suspend fun updatePlaylistMediaProviderType(
        playlist: Playlist,
        mediaProviderType: MediaProviderType
    )

    suspend fun updatePlaylistExternalId(
        playlist: Playlist,
        externalId: String?
    )
}

enum class PlaylistSortOrder : Serializable {
    Default
    ;

    val comparator: Comparator<Playlist>
        get() {
            return when (this) {
                Default -> defaultComparator
            }
        }

    companion object {
        val defaultComparator: Comparator<Playlist> by lazy { compareBy { playlist -> playlist.id } }
    }
}
