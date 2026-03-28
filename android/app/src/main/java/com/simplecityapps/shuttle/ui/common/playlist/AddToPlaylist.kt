package com.simplecityapps.shuttle.ui.common.playlist

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.playlistmenu.PlaylistData
import kotlinx.coroutines.flow.firstOrNull

class AddToPlaylist(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val queueManager: QueueOperations,
    private val ignorePlaylistDuplicates: () -> Boolean,
) {
    sealed interface Result {
        data class Success(val playlist: Playlist, val playlistData: PlaylistData) : Result
        data class DuplicatesFound(
            val playlist: Playlist,
            val playlistData: PlaylistData,
            val deduplicatedSongs: PlaylistData.Songs,
            val duplicates: List<Song>,
        ) : Result
        data class Failure(val message: String?) : Result
    }

    suspend operator fun invoke(
        playlist: Playlist,
        playlistData: PlaylistData,
        ignoreDuplicates: Boolean = false,
    ): Result {
        val songs = resolveSongs(playlistData)
        if (songs.isEmpty()) return Result.Failure(null)

        if (!ignoreDuplicates && !ignorePlaylistDuplicates()) {
            val existing = playlistRepository.getSongsForPlaylist(playlist)
                .firstOrNull().orEmpty()
            val duplicates = songs.filter { song -> existing.any { it.song.id == song.id } }
            if (duplicates.isNotEmpty()) {
                return Result.DuplicatesFound(
                    playlist, playlistData,
                    PlaylistData.Songs(songs - duplicates.toSet()), duplicates,
                )
            }
        }

        return try {
            playlistRepository.addToPlaylist(playlist, songs)
            Result.Success(playlist, playlistData)
        } catch (e: Exception) {
            Result.Failure(e.message)
        }
    }

    suspend fun resolveSongs(playlistData: PlaylistData): List<Song> {
        return when (playlistData) {
            is PlaylistData.Songs -> playlistData.data
            is PlaylistData.Albums -> songRepository
                .getSongs(SongQuery.AlbumGroupKeys(playlistData.data.map { SongQuery.AlbumGroupKey(it.groupKey) }))
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.AlbumArtists -> songRepository
                .getSongs(SongQuery.ArtistGroupKeys(playlistData.data.map { SongQuery.ArtistGroupKey(it.groupKey) }))
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.Genres -> genreRepository
                .getSongsForGenres(playlistData.data.map { it.name }, SongQuery.All())
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)
            is PlaylistData.Queue -> queueManager.getQueue().map { it.song }
        }
    }
}
