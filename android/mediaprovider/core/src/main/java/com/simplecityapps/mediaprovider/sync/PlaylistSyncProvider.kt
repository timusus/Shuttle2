package com.simplecityapps.mediaprovider.sync

import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.model.MediaProviderType
import kotlinx.datetime.Instant

/**
 * Interface for bidirectional playlist synchronization with media servers.
 * Implementations provide server-specific APIs for creating, updating, and deleting playlists.
 */
interface PlaylistSyncProvider {
    val type: MediaProviderType

    /**
     * Create a new playlist on the remote server.
     *
     * @param name The playlist name
     * @param songExternalIds External IDs of songs to add to the playlist
     * @return The external ID of the created playlist
     */
    suspend fun createPlaylist(
        name: String,
        songExternalIds: List<String>
    ): NetworkResult<String>

    /**
     * Update playlist metadata (name, description, etc.).
     *
     * @param externalId Remote playlist ID
     * @param name New playlist name
     */
    suspend fun updatePlaylistMetadata(
        externalId: String,
        name: String
    ): NetworkResult<Unit>

    /**
     * Add songs to an existing playlist.
     *
     * @param externalId Remote playlist ID
     * @param songExternalIds External IDs of songs to add
     */
    suspend fun addSongsToPlaylist(
        externalId: String,
        songExternalIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Remove songs from a playlist.
     * Note: Some servers require playlist-item-specific IDs (not song IDs).
     *
     * @param externalId Remote playlist ID
     * @param playlistItemIds Server-specific playlist item IDs
     */
    suspend fun removeSongsFromPlaylist(
        externalId: String,
        playlistItemIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Reorder songs in a playlist.
     *
     * @param externalId Remote playlist ID
     * @param orderedSongExternalIds Song external IDs in the new order
     */
    suspend fun reorderPlaylistSongs(
        externalId: String,
        orderedSongExternalIds: List<String>
    ): NetworkResult<Unit>

    /**
     * Delete a playlist from the remote server.
     *
     * @param externalId Remote playlist ID
     */
    suspend fun deletePlaylist(externalId: String): NetworkResult<Unit>

    /**
     * Get the current state of a playlist for conflict detection.
     *
     * @param externalId Remote playlist ID
     * @return Current playlist state including songs and last modified timestamp
     */
    suspend fun getPlaylistState(
        externalId: String
    ): NetworkResult<PlaylistRemoteState>
}

/**
 * Represents the remote state of a playlist for conflict detection.
 */
data class PlaylistRemoteState(
    val externalId: String,
    val name: String,
    val songExternalIds: List<String>, // Ordered list of song IDs
    val lastModified: Instant
)
