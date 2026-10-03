package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.repository.songs.comparator
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.SongSortOrder
import com.simplecityapps.shuttle.ui.screens.library.folders.ResolveFolderSongs
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * The songs a [MediaSelection] stands for, in the order an action should use them: albums, artists and genres in
 * the default song order (artists in the selection's order), playlists in their own order, folders in the folder browser's order.
 */
@Inject
class ResolveSongs(
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueOperations: QueueOperations,
    private val resolveFolderSongs: ResolveFolderSongs,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend operator fun invoke(selection: MediaSelection): List<Song> = when (selection) {
        is MediaSelection.Songs -> selection.songs

        // Read by id through the album index (loadSongs) rather than filtered out of the whole library's song list,
        // which the first play after launch would otherwise wait for the database to read in full.
        is MediaSelection.Albums ->
            songRepository
                .loadSongs(SongQuery.AlbumGroupKeys(selection.albums.map { SongQuery.AlbumGroupKey(it.groupKey) }))
                .sortedWith(SongSortOrder.Default.comparator)

        // Artist by artist in the selection's order (the library's current sort, for the controls row), each artist's
        // songs in the default song order. A song credited to several selected artists sorts with the earliest of them.
        // Ranked once per song (not searched per comparison) and sorted off the main thread: a big library has tens of
        // thousands of songs and thousands of artists.
        is MediaSelection.AlbumArtists -> {
            val songs = songRepository.loadSongs(SongQuery.ArtistGroupKeys(selection.albumArtists.map { SongQuery.ArtistGroupKey(it.groupKey) }))
            withContext(defaultDispatcher) {
                val artistIndex = HashMap<AlbumArtistGroupKey, Int>()
                selection.albumArtists.forEachIndexed { index, artist -> artistIndex.putIfAbsent(artist.groupKey, index) }
                val ranked = songs.map { song ->
                    val rank = minOf(
                        artistIndex[song.albumArtistGroupKey] ?: Int.MAX_VALUE,
                        song.artistCredits.minOfOrNull { artistIndex[it.groupKey] ?: Int.MAX_VALUE } ?: Int.MAX_VALUE,
                    )
                    rank to song
                }
                val songOrder = SongSortOrder.Default.comparator
                ranked.sortedWith { x, y -> if (x.first != y.first) x.first.compareTo(y.first) else songOrder.compare(x.second, y.second) }.map { it.second }
            }
        }

        is MediaSelection.Genres ->
            genreRepository
                .getSongsForGenres(selection.genres.map { it.name }, SongQuery.All())
                .firstOrNull().orEmpty()
                .sortedWith(SongSortOrder.Default.comparator)

        is MediaSelection.Playlists ->
            selection.playlists.flatMap { playlist ->
                playlistRepository.getSongsForPlaylist(playlist).firstOrNull().orEmpty().map { it.song }
            }

        is MediaSelection.SongsMatching -> songRepository.loadSongs(selection.query).sortedWith(selection.query.sortOrder.comparator)

        is MediaSelection.Folders -> resolveFolderSongs(selection.paths)

        is MediaSelection.Queue -> queueOperations.getQueue().map { it.song }
    }
}
