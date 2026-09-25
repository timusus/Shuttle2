package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import javax.inject.Named
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** What Home shows; each shelf is empty when there's nothing to put on it. */
data class HomeSectionsData(
    val songs: List<Song>,
    val recentlyPlayed: List<Album>,
    val recentlyAdded: List<Album>,
    val mostPlayed: List<Album>,
    val somethingDifferent: List<AlbumArtist>,
)

/**
 * Builds Home's shelves from the whole library, re-emitting as it changes:
 * - Recently played: albums by the last time one of their songs played.
 * - Recently added: albums by their newest song's modification time.
 * - Most played: albums played at least [MostPlayedMinimum] times, most first.
 * - Something different: artists never played, shuffled with the app's per-launch [seed] so the shelf holds still
 *   while the app runs.
 */
class HomeSections @Inject constructor(
    private val albumRepository: AlbumRepository,
    private val albumArtistRepository: AlbumArtistRepository,
    private val songRepository: SongRepository,
    @Named("randomSeed") private val seed: Long,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(): Flow<HomeSectionsData> = combine(
        songRepository.getSongs(SongQuery.All()),
        albumRepository.getAlbums(AlbumQuery.All()),
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()),
    ) { songs, albums, albumArtists ->
        val allSongs = songs.orEmpty()
        HomeSectionsData(
            songs = allSongs,
            recentlyPlayed = albums
                .filter { it.lastSongPlayed != null }
                .sortedByDescending { it.lastSongPlayed }
                .take(ShelfSize),
            recentlyAdded = recentlyAdded(albums, allSongs),
            mostPlayed = albums
                .filter { it.playCount >= MostPlayedMinimum }
                .sortedByDescending { it.playCount }
                .take(ShelfSize),
            somethingDifferent = albumArtists
                .filter { it.playCount == 0 }
                .shuffled(Random(seed))
                .take(ShelfSize),
        )
    }.flowOn(dispatcher)

    private fun recentlyAdded(albums: List<Album>, songs: List<Song>): List<Album> {
        val albumsByKey = albums.filter { it.groupKey != null }.associateBy { it.groupKey }
        return songs
            .filter { it.lastModified != null }
            .groupBy { it.albumGroupKey }
            .mapValues { (_, albumSongs) -> albumSongs.maxOf { it.lastModified!! } }
            .entries
            .sortedByDescending { it.value }
            .mapNotNull { albumsByKey[it.key] }
            .take(ShelfSize)
    }

    companion object {
        const val ShelfSize = 20
        const val MostPlayedMinimum = 2
    }
}
