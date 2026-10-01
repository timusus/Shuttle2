package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveArtistAlbums
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val loadingState: LoadingState = LoadingState.Loading,
    /** The album artwork's seed, which tints the screen when Colour from artwork is on. */
    val seed: ArtworkSeed = ArtworkSeed.None,
    /** The album artist's other albums, newest first, for the More by shelf; empty when the album has no album artist. */
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
    observeArtistAlbums: ObserveArtistAlbums,
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

    /** The album artist's own albums but this one, newest first; the Ready state doesn't wait on it. */
    private val moreByArtist: Flow<List<Album>> = groupKey?.albumArtistGroupKey?.takeIf { it.key != null }
        ?.let { artistKey -> observeArtistAlbums(artistKey).map { artist -> artist.albums.filter { it.groupKey != groupKey } } }
        ?: flowOf(emptyList())

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songs,
        observeAlbums(AlbumQuery.AlbumGroupKey(groupKey)),
        observeCurrentSong(),
        observeArtworkSeed(songs.map { it.firstOrNull() }),
        moreByArtist,
    ) { songs, albums, currentSong, seed, moreByArtist ->
        AlbumDetailUiState(
            album = albums.firstOrNull(),
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
