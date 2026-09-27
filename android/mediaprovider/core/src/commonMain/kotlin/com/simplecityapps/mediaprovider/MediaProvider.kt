package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.Flow

interface MediaProvider {
    val type: MediaProviderType

    /**
     * @param existingSongs the songs this provider imported last time, so it can skip expensive work for files that haven't changed
     */
    fun findSongs(existingSongs: List<Song>): Flow<FlowEvent<List<Song>, MessageProgress>>

    /**
     * Maps [existingSongs] stored under an identity this provider no longer produces to the path [findSongs] now returns
     * for the same file, so an upgrade keeps their history. Songs it can't match are left out, and the import removes
     * them as missing. Runs before every song import, so it must return nothing once no old identities are left.
     */
    suspend fun remapLegacySongs(existingSongs: List<Song>): List<SongPathRemap> = emptyList()

    fun findPlaylists(existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>>
}
