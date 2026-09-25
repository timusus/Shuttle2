package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.StringComparison
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.screens.home.search.AlbumJaroSimilarity
import com.simplecityapps.shuttle.ui.screens.home.search.ArtistJaroSimilarity
import com.simplecityapps.shuttle.ui.screens.home.search.SongJaroSimilarity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The result types Search offers as filter chips. */
enum class SearchCategory { Artists, Albums, Songs, Genres, Playlists }

data class SearchResults(
    val artists: List<AlbumArtist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty() && genres.isEmpty() && playlists.isEmpty()
}

/**
 * Fuzzy-searches the library: each enabled [SearchCategory] keeps the items whose names score above
 * [StringComparison.threshold] on Jaro-Winkler similarity, best match first. Re-emits as the library changes.
 */
class SearchLibrary @Inject constructor(
    private val albumArtistRepository: AlbumArtistRepository,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val genreRepository: GenreRepository,
    private val playlistRepository: PlaylistRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(query: String, categories: Set<SearchCategory>): Flow<SearchResults> {
        if (query.isBlank()) return flowOf(SearchResults())

        val artists = category(SearchCategory.Artists in categories) {
            albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()).map { rankArtists(it, query) }
        }
        val albums = category(SearchCategory.Albums in categories) {
            albumRepository.getAlbums(AlbumQuery.All()).map { rankAlbums(it, query) }
        }
        val songs = category(SearchCategory.Songs in categories) {
            songRepository.getSongs(SongQuery.All()).map { rankSongs(it.orEmpty(), query) }
        }
        val genres = category(SearchCategory.Genres in categories) {
            genreRepository.getGenres(GenreQuery.All()).map { genres -> rankByName(genres, query) { it.name } }
        }
        val playlists = category(SearchCategory.Playlists in categories) {
            playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)).map { playlists -> rankByName(playlists, query) { it.name } }
        }
        return combine(artists, albums, songs, genres, playlists) { artistResults, albumResults, songResults, genreResults, playlistResults ->
            SearchResults(artistResults, albumResults, songResults, genreResults, playlistResults)
        }.flowOn(dispatcher)
    }

    private fun <T> category(enabled: Boolean, results: () -> Flow<List<T>>): Flow<List<T>> = if (enabled) results() else flowOf(emptyList())

    private fun rankArtists(albumArtists: List<AlbumArtist>, query: String): List<AlbumArtist> = albumArtists
        .map { ArtistJaroSimilarity(it, query) }
        .filter { it.albumArtistNameJaroSimilarity.score > threshold || it.artistNameJaroSimilarity.score > threshold }
        .sortedWith(
            compareByDescending<ArtistJaroSimilarity> { it.artistNameJaroSimilarity.aboveThreshold() }
                .thenByDescending { it.albumArtistNameJaroSimilarity.aboveThreshold() },
        )
        .map { it.albumArtist }

    private fun rankAlbums(albums: List<Album>, query: String): List<Album> = albums
        .map { AlbumJaroSimilarity(it, query) }
        .filter { it.nameJaroSimilarity.score > threshold || it.albumArtistNameJaroSimilarity.score > threshold || it.artistNameJaroSimilarity.score > threshold }
        .sortedWith(
            compareByDescending<AlbumJaroSimilarity> { it.nameJaroSimilarity.score }
                .thenByDescending { it.artistNameJaroSimilarity.aboveThreshold() }
                .thenByDescending { it.albumArtistNameJaroSimilarity.aboveThreshold() },
        )
        .map { it.album }

    private fun rankSongs(songs: List<Song>, query: String): List<Song> = songs
        .asSequence()
        .map { SongJaroSimilarity(it, query) }
        .filter {
            it.nameJaroSimilarity.score > threshold ||
                it.albumArtistNameJaroSimilarity.score > threshold ||
                it.artistNameJaroSimilarity.score > threshold ||
                it.albumNameJaroSimilarity.score > threshold
        }
        .sortedWith(
            compareByDescending<SongJaroSimilarity> { it.nameJaroSimilarity.aboveThreshold() }
                .thenByDescending { it.albumNameJaroSimilarity.aboveThreshold() }
                .thenByDescending { it.artistNameJaroSimilarity.aboveThreshold() }
                .thenByDescending { it.albumArtistNameJaroSimilarity.aboveThreshold() },
        )
        .map { it.song }
        .toList()

    private fun <T> rankByName(items: List<T>, query: String, name: (T) -> String?): List<T> = items
        .mapNotNull { item -> name(item)?.let { item to StringComparison.jaroWinklerMultiDistance(query, it).score } }
        .filter { (_, score) -> score > threshold }
        .sortedByDescending { (_, score) -> score }
        .map { (item, _) -> item }

    private fun StringComparison.JaroSimilarity.aboveThreshold(): Double = if (score > threshold) score else 0.0

    private companion object {
        val threshold = StringComparison.threshold
    }
}
