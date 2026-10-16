package com.simplecityapps.shuttle.ui.screens.library.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.ComposerWorks
import com.simplecityapps.shuttle.model.FindComposerWorks
import com.simplecityapps.shuttle.sorting.ArtistSongComparator
import com.simplecityapps.shuttle.ui.actions.ObserveAlbums
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ComposerUiState(
    val loading: Boolean = true,
    /** Null when the library holds no classical work by the composer. */
    val composer: ComposerWorks? = null,
    val albums: List<Album> = emptyList(),
)

/** A composer's works and albums, found from the raw composer tag the route carries. */
class ComposerViewModel @AssistedInject constructor(
    @Assisted private val composer: String,
    observeSongs: ObserveSongs,
    observeAlbums: ObserveAlbums,
    findComposerWorks: FindComposerWorks,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey(Factory::class)
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(composer: String): ComposerViewModel
    }

    // The whole library is scanned, so off the main thread.
    private val works: Flow<ComposerWorks?> = observeSongs()
        .map { library -> findComposerWorks(composer, library) }
        .flowOn(ioDispatcher)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ComposerUiState> = works
        .flatMapLatest { found ->
            if (found == null) {
                flowOf(ComposerUiState(loading = false))
            } else {
                observeAlbums(AlbumQuery.AlbumGroupKeys(found.albums.map { AlbumQuery.AlbumGroupKey(it) })).map { albums ->
                    ComposerUiState(loading = false, composer = found, albums = albums.sortedWith(ArtistSongComparator.albumNewest))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComposerUiState())
}
