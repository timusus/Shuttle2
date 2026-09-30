package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.SongImportStateProvider
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * The whole library as one [SearchIndex] of [AlbumArtist]s, [Album]s, [Song]s, [Playlist]s and [Genre]s, rebuilt off
 * the main thread whenever the library's content changes. Playing a song moves play counts and last-played times in
 * three repositories, which would rebuild the whole index mid-search (#677), so those changes alone rebuild it at most
 * every [PLAY_REFRESH]: the play counts that weight the ranking catch up at the first play after that, or at the next
 * content change. Content changes are debounced, so an import's songs, albums and artists make one rebuild. Shared by every search screen; it stops
 * following the library a minute after the last one goes, so reopening search soon after reuses the index, unless
 * [warmUp] is holding it.
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
    private val importState: SongImportStateProvider,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var warming: Job? = null

    @OptIn(FlowPreview::class)
    val index: Flow<SearchIndex<Any>> = combine(
        // Every artist, album artist or only credited (featured, on compilations), each once (#637)
        albumArtistRepository.getAlbumArtists(AlbumArtistQuery.Credited())
            .distinctUntilChanged(PlayThrottle<AlbumArtist> { it.copy(playCount = 0) }::same),
        albumRepository.getAlbums(AlbumQuery.All())
            .distinctUntilChanged(PlayThrottle<Album> { it.copy(playCount = 0, lastSongPlayed = null, lastSongCompleted = null) }::same),
        songRepository.getSongs(SongQuery.All())
            .distinctUntilChanged(PlayThrottle<Song> { it.copy(playCount = 0, lastPlayed = null, lastCompleted = null, playbackPosition = 0) }::same),
        genreRepository.getGenres(GenreQuery.All()),
        playlistRepository.getPlaylists(PlaylistQuery.All(mediaProviderType = null)),
    ) { artists, albums, songs, genres, playlists ->
        // Insertion order breaks the last ties, so an artist outranks an equally good album, song and so on.
        artists.map(::document) + albums.map(::document) + songs.orEmpty().map(::document) + playlists.map(::document) + genres.map(::document)
    }
        .debounceAfterFirst()
        .conflate()
        .map { SearchIndex.build(it) }
        .flowOn(dispatcher)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 60_000), replay = 1)

    /**
     * Builds the index ahead of the first search, and keeps it current for the process's life, so the first query
     * doesn't wait for a build (0.34 s for 18k songs). Waits for the library to have songs, so an empty one builds
     * nothing, and holds off until the launch import has been quiet for [WARM_UP_DELAY] so it doesn't compete with it;
     * runs off the main thread. Safe to call again; it does nothing while already warming.
     */
    fun warmUp() {
        if (warming?.isActive == true) return
        warming = scope.launch(dispatcher) {
            importState.songImportState.debounce(WARM_UP_DELAY).first { it !is SongImportState.ImportProgress }
            songRepository.getSongs(SongQuery.All()).first { !it.isNullOrEmpty() }
            index.collect {}
        }
    }

    /** [this] with the first emission passed straight through and later ones debounced by [REBUILD_DEBOUNCE]. */
    @OptIn(FlowPreview::class)
    private fun <T> Flow<T>.debounceAfterFirst(): Flow<T> = flow {
        var first = true
        emitAll(
            debounce {
                (if (first) 0.milliseconds else REBUILD_DEBOUNCE).also { first = false }
            }
        )
    }

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

    /**
     * Treats a list as unchanged when only what playing its items changes differs ([withoutPlays] clears that), except
     * that the first such change after [PLAY_REFRESH] counts, so the play counts that weight the ranking don't freeze.
     */
    private inner class PlayThrottle<T>(private val withoutPlays: (T) -> T) {
        private var lastChange = timeSource.markNow()

        fun same(old: List<T>?, new: List<T>?): Boolean {
            if (old === new) return true
            if (!sameIgnoringPlays(old, new, withoutPlays)) {
                lastChange = timeSource.markNow()
                return false
            }
            if (lastChange.elapsedNow() < PLAY_REFRESH || old == new) return true
            lastChange = timeSource.markNow()
            return false
        }
    }

    private fun <T> sameIgnoringPlays(old: List<T>?, new: List<T>?, withoutPlays: (T) -> T): Boolean = old === new ||
        (old != null && new != null && old.size == new.size && old.indices.all { withoutPlays(old[it]) == withoutPlays(new[it]) })

    private fun fields(vararg fields: Pair<SearchField, String?>): List<Pair<SearchField, String?>> = fields.toList()

    private companion object {
        val PLAY_REFRESH = 10.minutes
        val REBUILD_DEBOUNCE = 500.milliseconds
        val WARM_UP_DELAY = 5.seconds
    }
}
