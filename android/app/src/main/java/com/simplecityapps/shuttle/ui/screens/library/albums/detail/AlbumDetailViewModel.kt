package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    enum class LoadingState { Loading, Ready, Empty }
}

/**
 * One album's songs and header, loaded by [groupKey], the key its route carries. The screen's actions go
 * through its MediaActionsHost, so this only derives state.
 */
@HiltViewModel(assistedFactory = AlbumDetailViewModel.Factory::class)
class AlbumDetailViewModel @AssistedInject constructor(
    @Assisted private val groupKey: AlbumGroupKey?,
    observeSongs: ObserveSongs,
    observeAlbums: ObserveAlbums,
    observeCurrentSong: ObserveCurrentSong,
    observeArtworkSeed: ObserveArtworkSeed,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(groupKey: AlbumGroupKey?): AlbumDetailViewModel
    }

    private val songs = observeSongs(SongQuery.AlbumGroupKey(key = groupKey))
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        songs,
        observeAlbums(AlbumQuery.AlbumGroupKey(groupKey)),
        observeCurrentSong(),
        observeArtworkSeed(songs.map { it.firstOrNull() }),
    ) { songs, albums, currentSong, seed ->
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlbumDetailUiState(),
    )
}
