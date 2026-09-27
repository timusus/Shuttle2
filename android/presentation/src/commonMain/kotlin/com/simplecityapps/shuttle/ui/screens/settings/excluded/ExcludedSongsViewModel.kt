package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionHandler
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExcludedSongsUiState(
    val songs: List<Song> = emptyList(),
    val loading: Boolean = true
)

/** The songs hidden from the library, and the way back in for each. */
@ViewModelKey(ExcludedSongsViewModel::class)
@ContributesIntoMap(AppScope::class)
class ExcludedSongsViewModel @Inject constructor(
    observeSongs: ObserveSongs,
    private val mediaActionHandler: MediaActionHandler
) : ViewModel() {
    val uiState: StateFlow<ExcludedSongsUiState> = observeSongs(SongQuery.All(includeExcluded = true))
        .map { songs ->
            ExcludedSongsUiState(
                songs = songs.filter { it.blacklisted }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }),
                loading = false
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExcludedSongsUiState())

    fun onInclude(song: Song) {
        viewModelScope.launch { mediaActionHandler.handle(MediaAction.Include(MediaSelection.Songs(song))) }
    }

    fun onIncludeAll() {
        val songs = uiState.value.songs
        viewModelScope.launch { mediaActionHandler.handle(MediaAction.Include(MediaSelection.Songs(songs))) }
    }
}
