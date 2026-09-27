package com.simplecityapps.mediaprovider.repository.smartplaylists

import com.simplecityapps.shuttle.model.UserSmartPlaylist
import com.simplecityapps.shuttle.smartplaylist.SmartRules
import kotlinx.coroutines.flow.Flow

/** The user's smart playlists. One whose stored rules this version can't read is left out rather than failing the rest. */
interface SmartPlaylistRepository {
    /** Every smart playlist, by name. */
    fun getSmartPlaylists(): Flow<List<UserSmartPlaylist>>

    /** The smart playlist with [id], or null once it's deleted. */
    fun getSmartPlaylist(id: Long): Flow<UserSmartPlaylist?>

    suspend fun create(
        name: String,
        rules: SmartRules,
    ): UserSmartPlaylist

    /** Saves [smartPlaylist]'s name and rules. */
    suspend fun update(smartPlaylist: UserSmartPlaylist)

    suspend fun delete(id: Long)
}
