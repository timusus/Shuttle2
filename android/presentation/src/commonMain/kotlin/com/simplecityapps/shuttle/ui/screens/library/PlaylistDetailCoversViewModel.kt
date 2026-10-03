package com.simplecityapps.shuttle.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistQuery
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.ObservePlaylistCovers
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The songs whose covers make up a playlist's mosaic (#652), the same ones its Library row draws: its first four from
 * different albums, in the playlist's order. Apart from [PlaylistDetailViewModel] so a screen that draws no mosaic
 * (Android's) never runs the query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailCoversViewModel @AssistedInject constructor(
    @Assisted playlistId: Long,
    observePlaylists: ObservePlaylists,
    observePlaylistCovers: ObservePlaylistCovers,
) : ViewModel() {
    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(playlistId: Long): PlaylistDetailCoversViewModel
    }

    val uiState: StateFlow<List<Song>> = observePlaylists(PlaylistQuery.PlaylistId(playlistId))
        .map { it.firstOrNull() }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { playlist ->
            if (playlist == null) flowOf(emptyList()) else observePlaylistCovers(listOf(playlist)).map { it[playlist.id].orEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
