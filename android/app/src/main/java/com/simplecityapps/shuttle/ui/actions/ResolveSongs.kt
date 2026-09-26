package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

/**
 * The songs a [MediaSelection] stands for, in the order an action should use them: albums, artists and genres in
 * the default song order, playlists in their own order, folders in the folder browser's order.
 */
class ResolveSongs @Inject constructor(
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueOperations: QueueOperations,
    private val resolveFolderSongs: ResolveFolderSongs,
) {
    suspend operator fun invoke(selection: MediaSelection): List<Song> = when (selection) {
        is MediaSelection.Songs -> selection.songs

        is MediaSelection.Albums ->
            songRepository
                .getSongs(SongQuery.AlbumGroupKeys(selection.albums.map { SongQuery.AlbumGroupKey(it.groupKey) }))
                .filterNotNull()
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)

        is MediaSelection.AlbumArtists ->
            songRepository
                .getSongs(SongQuery.ArtistGroupKeys(selection.albumArtists.map { SongQuery.ArtistGroupKey(it.groupKey) }))
                .filterNotNull()
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)

        is MediaSelection.Genres ->
            genreRepository
                .getSongsForGenres(selection.genres.map { it.name }, SongQuery.All())
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)

        is MediaSelection.Playlists ->
            selection.playlists.flatMap { playlist ->
                playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            }

        is MediaSelection.Folders -> resolveFolderSongs(selection.paths)

        is MediaSelection.Queue -> queueOperations.getQueue().map { it.song }
    }
}
