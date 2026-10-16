package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.mediaprovider.repository.artists.AlbumArtistQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.isClassicalBlock
import com.simplecityapps.shuttle.model.performersLine
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtists
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
    /** The album artwork's seed, which tints the screen when Colour from artwork is on. */
    val seed: ArtworkSeed = ArtworkSeed.None,
    /** The album artist's other albums, newest first, for the More by shelf; empty when the album has no album artist or is a compilation. */
    val moreByArtist: List<Album> = emptyList(),
    /** The album artists [moreByArtist] is from, by name in the album's order ("A", "B" for "A feat. B"), for the shelf's title. */
    val moreByArtistNames: List<String> = emptyList(),
    /** Classical display is on: the screen groups movements under their work. */
    val classical: Boolean = false,
    /** The classical page's rows; null when classical display is off or the album has no work blocks, which then render as plain songs. */
    val rows: AlbumRows? = null,
) {
    // Swift sees no default arguments.
    constructor(
        album: Album?,
        songs: List<Song>,
        currentSong: Song?,
        loadingState: LoadingState,
        seed: ArtworkSeed,
        moreByArtist: List<Album>,
        moreByArtistNames: List<String>,
        classical: Boolean,
    ) : this(album, songs, currentSong, loadingState, seed, moreByArtist, moreByArtistNames, classical, null)

    /** The first conductor or performer credit among the songs, for the subtitle; null when classical display is off, the songs aren't a classical block or none is tagged. */
    val performers: String? get() = if (classical && songs.isClassicalBlock()) songs.firstNotNullOfOrNull { it.performersLine() } else null

    /** What playing this screen's songs starts the queue from (#633). */
    val playContext: PlayContext get() = album?.playContext ?: PlayContext.None

    enum class LoadingState { Loading, Ready, Empty }
}

/**
 * One album's songs and header, loaded by [groupKey], the key its route carries. The screen's actions go
 * through its MediaActionsHost, so this only derives state.
 */
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumGroupKey?,
    observeSongs: ObserveSongs,
    observeAlbums: ObserveAlbums,
    observeArtists: ObserveArtists,
    observeCurrentSong: ObserveCurrentSong,
    observeArtworkSeed: ObserveArtworkSeed,
    observeSetting: ObserveSetting,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(groupKey: AlbumGroupKey?): AlbumDetailViewModel
    }

    private val songs = observeSongs(SongQuery.AlbumGroupKey(key = groupKey))
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val album = observeAlbums(AlbumQuery.AlbumGroupKey(groupKey))
        .map { it.firstOrNull() }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Its album artists' own albums but this one, newest first: each of an album of several, and A's of "A feat. B". Starts
     * empty so the Ready state never waits on it; it is empty for an album with no album artist, and for a compilation,
     * whose "Various Artists" is nobody's catalogue.
     */
    private val moreByArtistKeys: Flow<Set<AlbumArtistGroupKey>> = album
        .map { album -> album?.albumArtistKeys.orEmpty().filterTo(LinkedHashSet()) { it.key != null && it.key != AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS) } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val moreByArtist: Flow<List<Album>> = moreByArtistKeys
        .flatMapLatest { artistKeys ->
            if (artistKeys.isEmpty()) {
                flowOf(emptyList())
            } else {
                observeAlbums(AlbumQuery.ArtistGroupKeys(artistKeys))
                    .map { albums -> albums.filter { it.groupKey != groupKey }.sortedWith(ArtistSongComparator.albumNewest) }
            }
        }
        .onStart { emit(emptyList()) }

    /** The names of [moreByArtistKeys], in their order, so the shelf is titled from the artists it lists, not from the album's raw tag. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val moreByArtistNames: Flow<List<String>> = moreByArtistKeys
        .flatMapLatest { artistKeys ->
            if (artistKeys.isEmpty()) {
                flowOf(emptyList())
            } else {
                observeArtists(AlbumArtistQuery.Credited()).map { artists ->
                    val namesByKey = artists.associate { it.groupKey to it.name }
                    artistKeys.mapNotNull { namesByKey[it]?.takeIf { name -> name.isNotBlank() } }
                }
            }
        }
        .distinctUntilChanged()
        .onStart { emit(emptyList()) }

    /** Works (by first song id) whose collapse state the user flipped from its default. */
    private val toggledWorks = MutableStateFlow<Set<Long>>(emptySet())

    fun onToggleWork(firstSongId: Long) {
        toggledWorks.update { if (firstSongId in it) it - firstSongId else it + firstSongId }
    }

    private class Extras(val moreByArtist: List<Album>, val moreByArtistNames: List<String>, val classical: Boolean, val grouped: Grouped)

    /** The rows with the songs they were built from: row play indices point into these, so the screen must play from this list. */
    private class Grouped(val songs: List<Song>, val rows: AlbumRows?)

    private val classicalDisplay = observeSetting(AppearanceSettings.ClassicalDisplay)

    /** The grouping, worked out once per change to the songs, album or settings; a song change in the player leaves it alone. */
    private val groupedRows: Flow<Grouped> = combine(songs, album, classicalDisplay, toggledWorks) { songs, album, classical, toggled ->
        Grouped(songs, if (classical) buildAlbumRows(songs, album?.albumArtist ?: album?.friendlyArtistName, null, toggled) else null)
    }

    private fun AlbumRows.withPlaying(songId: Long?): AlbumRows {
        if (songId == null) return this
        return copy(
            rows = rows.map { row ->
                when (row) {
                    is AlbumRow.Track -> row.copy(playing = row.song.id == songId)
                    is AlbumRow.Movement -> row.copy(playing = row.song.id == songId)
                    is AlbumRow.WorkHeader -> row.copy(playing = row.songs.any { it.id == songId })
                    else -> row
                }
            }
        )
    }

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songs,
        album,
        observeCurrentSong(),
        observeArtworkSeed(songs.map { it.firstOrNull() }),
        combine(moreByArtist, moreByArtistNames, classicalDisplay, groupedRows, ::Extras),
    ) { _, album, currentSong, seed, extras ->
        val songs = extras.grouped.songs
        AlbumDetailUiState(
            album = album,
            songs = songs,
            currentSong = currentSong,
            loadingState = if (songs.isEmpty()) {
                AlbumDetailUiState.LoadingState.Empty
            } else {
                AlbumDetailUiState.LoadingState.Ready
            },
            seed = seed,
            moreByArtist = extras.moreByArtist,
            // A credited key with no artist row or a blank name resolves to nothing: fall back to the raw tag
            moreByArtistNames = extras.moreByArtistNames.ifEmpty { listOfNotNull(album?.albumArtist?.takeIf { it.isNotBlank() }) },
            classical = extras.classical,
            rows = extras.grouped.rows?.withPlaying(currentSong?.id),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )
}
