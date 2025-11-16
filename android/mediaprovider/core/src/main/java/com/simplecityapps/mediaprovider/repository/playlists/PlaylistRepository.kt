package com.simplecityapps.mediaprovider.repository.playlists

import android.net.Uri
import com.simplecityapps.mediaprovider.PlaylistExporter
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

    /**
     * Export a playlist to an m3u file at the specified URI.
     *
     * @param playlist The playlist to export
     * @param destinationUri URI where the m3u file should be written
     * @param pathResolver Optional function to resolve file paths for songs
     * @return Result indicating success or failure
     */
    suspend fun exportPlaylistToUri(
        playlist: Playlist,
        destinationUri: Uri,
        pathResolver: ((Song) -> String?)? = null
    ): PlaylistExporter.ExportResult

    /**
     * Export a playlist to a directory, creating a new m3u file.
     *
     * @param playlist The playlist to export
     * @param directoryUri URI of the directory where the file should be created
     * @param pathResolver Optional function to resolve file paths for songs
     * @return Result indicating success or failure
     */
    suspend fun exportPlaylistToDirectory(
        playlist: Playlist,
        directoryUri: Uri,
        pathResolver: ((Song) -> String?)? = null
    ): PlaylistExporter.ExportResult

    /**
     * Generate m3u content for a playlist as a string.
     *
     * @param playlist The playlist to export
     * @param pathResolver Optional function to resolve file paths for songs
     * @return M3U file content as a string, or null if no valid songs
     */
    suspend fun generatePlaylistM3uContent(
        playlist: Playlist,
        pathResolver: ((Song) -> String?)? = null
    ): String?
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
