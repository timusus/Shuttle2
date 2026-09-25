package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.albums.AlbumRepository
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.genres.GenreQuery
import com.simplecityapps.mediaprovider.repository.genres.GenreRepository
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.mediaprovider.search.SearchDocument
import com.simplecityapps.mediaprovider.search.SearchField
import com.simplecityapps.mediaprovider.search.SearchIndex
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * The whole library as one [SearchIndex] of [AlbumArtist]s, [Album]s, [Song]s, [Playlist]s and [Genre]s, rebuilt off
 * the main thread whenever a repository emits. Shared by every search screen; it stops following the library a minute
 * after the last one goes, so reopening search soon after reuses the index.
 */
@Singleton
class LibrarySearchIndex @Inject constructor(
    albumArtistRepository: AlbumArtistRepository,
    albumRepository: AlbumRepository,
    songRepository: SongRepository,
    genreRepository: GenreRepository,
    playlistRepository: PlaylistRepository,
    @AppCoroutineScope scope: CoroutineScope,
    @IoDispatcher dispatcher: CoroutineDispatcher,
) {
    val index: Flow<SearchIndex<Any>> = combine(
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.All()),
        albumRepository.getAlbums(AlbumQuery.All()),
        songRepository.getSongs(SongQuery.All()),
        genreRepository.getGenres(GenreQuery.All()),
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
    ) { artists, albums, songs, genres, playlists ->
        // Insertion order breaks the last ties, so an artist outranks an equally good album, song and so on.
        artists.map(::document) + albums.map(::document) + songs.orEmpty().map(::document) + playlists.map(::document) + genres.map(::document)
    }
        .conflate()
        .map { SearchIndex.build(it) }
        .flowOn(dispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 60_000), replay = 1)

    private fun document(artist: AlbumArtist) = SearchDocument<Any>(
        artist,
        (fields(SearchField.Name to artist.name) + artist.artists.map { SearchField.Artist to it }).distinct(),
        artist.playCount,
    )

    private fun document(album: Album) = SearchDocument<Any>(
        album,
        (fields(SearchField.Name to album.name, SearchField.Artist to album.albumArtist) + album.artists.map { SearchField.Artist to it }).distinct(),
        album.playCount,
    )

    private fun document(song: Song) = SearchDocument<Any>(
        song,
        (
            fields(SearchField.Name to song.name, SearchField.Artist to song.albumArtist, SearchField.Album to song.album) +
                song.artists.map { SearchField.Artist to it } +
                song.genres.map { SearchField.Genre to it }
            ).distinct(),
        song.playCount,
    )

    private fun document(playlist: Playlist) = SearchDocument<Any>(playlist, fields(SearchField.Name to playlist.name))

    private fun document(genre: Genre) = SearchDocument<Any>(genre, fields(SearchField.Name to genre.name))

    private fun fields(vararg fields: Pair<SearchField, String?>): List<Pair<SearchField, String?>> = fields.toList()
}
