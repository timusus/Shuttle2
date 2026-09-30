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
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * The whole library as one [SearchIndex] of [AlbumArtist]s, [Album]s, [Song]s, [Playlist]s and [Genre]s, rebuilt off
 * the main thread whenever the library's content changes. A change is built straight away, then later ones at most
 * every [REBUILD_INTERVAL], latest first, so an import's stream of changes makes a build every half second and ends on
 * its final state; while the library has no songs, every change builds at once. Playing a song moves play counts and
 * last-played times in three repositories, which would rebuild the whole index mid-search (#677), so those changes
 * alone rebuild it at most every [PLAY_REFRESH]: the play counts that weight the ranking catch up at the first play
 * after that, or at the next build for any other reason. Shared by every search screen; it stops following the library
 * a minute after the last one goes, so reopening search soon after reuses the index, unless [warmUp] is holding it.
 */
@SingleIn(AppScope::class)
class LibrarySearchIndex @Inject constructor(
    albumArtistRepository: AlbumArtistRepository,
    albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    genreRepository: GenreRepository,
    playlistRepository: PlaylistRepository,
    @AppCoroutineScope private val scope: CoroutineScope,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var warming: Job? = null

    val index: Flow<SearchIndex<Any>> = combine(
        // Every artist, album artist or only credited (featured, on compilations), each once (#637)
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.Credited()),
        albumRepository.getAlbums(AlbumQuery.All()),
        songRepository.getSongs(SongQuery.All()),
        genreRepository.getGenres(GenreQuery.All()),
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
        ::Library,
    )
        .buildIndexes()
        .flowOn(dispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 60_000), replay = 1)

    /**
     * Builds the index ahead of the first search, and keeps it current for the process's life, so the first query
     * doesn't wait for a build (0.34 s for 18k songs). Starts once the songs have loaded, off the main thread; the
     * rebuild interval bounds the work while an import runs. Safe to call again; it does nothing while already warming.
     */
    fun warmUp() {
        if (warming?.isActive == true) return
        warming = scope.launch(dispatcher) {
            songRepository.getSongs(SongQuery.All()).first { it != null }
            index.collect {}
        }
    }

    /**
     * An index of each [Library] worth building: the latest one when the last build is [REBUILD_INTERVAL] old, skipping
     * those that differ from the last build only by plays until [PLAY_REFRESH] after it. Only built ones make documents.
     */
    private fun Flow<Library>.buildIndexes(): Flow<SearchIndex<Any>> = flow {
        var built: Library? = null
        var builtAt = timeSource.markNow()
        conflate().collect { library ->
            val last = built
            if (last != null && last.sameIgnoringPlays(library) && (last == library || builtAt.elapsedNow() < PLAY_REFRESH)) return@collect
            emit(SearchIndex.build(library.documents()))
            built = library
            builtAt = timeSource.markNow()
            if (!library.songs.isNullOrEmpty()) delay(REBUILD_INTERVAL)
        }
    }

    /** Everything the index is built from; [songs] is null until the song repository has loaded. */
    private data class Library(
        val artists: List<AlbumArtist>,
        val albums: List<Album>,
        val songs: List<Song>?,
        val genres: List<Genre>,
        val playlists: List<Playlist>,
    ) {
        fun sameIgnoringPlays(other: Library): Boolean = genres == other.genres &&
            playlists == other.playlists &&
            sameIgnoringPlays(artists, other.artists) { it.copy(playCount = 0) } &&
            sameIgnoringPlays(albums, other.albums) { it.copy(playCount = 0, lastSongPlayed = null, lastSongCompleted = null) } &&
            sameIgnoringPlays(songs, other.songs) { it.copy(playCount = 0, lastPlayed = null, lastCompleted = null, playbackPosition = 0) }
    }

    // Insertion order breaks the last ties, so an artist outranks an equally good album, song and so on.
    private fun Library.documents(): List<SearchDocument<Any>> = artists.map(::document) + albums.map(::document) + songs.orEmpty().map(::document) + playlists.map(::document) + genres.map(::document)

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

    private companion object {
        val PLAY_REFRESH = 10.minutes
        val REBUILD_INTERVAL = 500.milliseconds

        /** Whether [old] and [new] differ at most by what playing their items changes, which [withoutPlays] clears. */
        fun <T> sameIgnoringPlays(old: List<T>?, new: List<T>?, withoutPlays: (T) -> T): Boolean = old === new ||
            (old != null && new != null && old.size == new.size && old.indices.all { withoutPlays(old[it]) == withoutPlays(new[it]) })
    }
}
