package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
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

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
    /** The album artwork's seed, which tints the screen when Colour from artwork is on. */
    val seed: ArtworkSeed = ArtworkSeed.None,
    /** The album artist's other albums, newest first, for the More by shelf; empty when the album has no album artist or is a compilation. */
    val moreByArtist: List<Album> = emptyList(),
) {
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
    observeCurrentSong: ObserveCurrentSong,
    observeArtworkSeed: ObserveArtworkSeed,
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
    @OptIn(ExperimentalCoroutinesApi::class)
    private val moreByArtist: Flow<List<Album>> = album
        .map { album -> album?.albumArtistKeys.orEmpty().filterTo(LinkedHashSet()) { it.key != null && it.key != AlbumIdentityRule.artistKey(AlbumIdentityRule.VARIOUS_ARTISTS) } }
        .distinctUntilChanged()
        .flatMapLatest { artistKeys ->
            if (artistKeys.isEmpty()) {
                flowOf(emptyList())
            } else {
                observeAlbums(AlbumQuery.ArtistGroupKeys(artistKeys))
                    .map { albums -> albums.filter { it.groupKey != groupKey }.sortedWith(ArtistSongComparator.albumNewest) }
            }
        }
        .onStart { emit(emptyList()) }

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songs,
        album,
        observeCurrentSong(),
        observeArtworkSeed(songs.map { it.firstOrNull() }),
        moreByArtist,
    ) { songs, album, currentSong, seed, moreByArtist ->
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
            moreByArtist = moreByArtist,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )
}
