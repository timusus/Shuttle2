package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import kotlinx.coroutines.flow.firstOrNull

/**
 * Adds a selection's songs to [Playlist]. Songs already in the playlist stop the add with [Result.DuplicatesFound],
 * unless the caller passes `ignoreDuplicates` ("Add anyway").
 */
class AddToPlaylist(
    private val playlistRepository: PlaylistRepository,
    private val resolveSongs: ResolveSongs,
) {
    sealed interface Result {
        data class Success(val playlist: Playlist, val songs: List<Song>) : Result

        /** Nothing was added: [duplicates] are already in [playlist], [nonDuplicates] are the rest of the selection. */
        data class DuplicatesFound(
            val playlist: Playlist,
            val nonDuplicates: List<Song>,
            val duplicates: List<Song>,
        ) : Result

        /** Nothing was added: the selection has no songs, or the repository failed ([message]). */
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(
        playlist: Playlist,
        selection: MediaSelection,
        ignoreDuplicates: Boolean = false,
    ): Result {
        val songs = resolveSongs(selection)
        if (songs.isEmpty()) return Result.Failure(null)

        if (!ignoreDuplicates) {
            val existingIds = playlistRepository.getSongsForPlaylist(playlist)
                .firstOrNull().orEmpty()
                .mapTo(mutableSetOf()) { it.song.id }
            val duplicates = songs.filter { it.id in existingIds }
            if (duplicates.isNotEmpty()) {
                return Result.DuplicatesFound(playlist, songs - duplicates.toSet(), duplicates)
            }
        }

        return try {
            playlistRepository.addToPlaylist(playlist, songs)
            Result.Success(playlist, songs)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }
}
