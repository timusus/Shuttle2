package com.simplecityapps.shuttle.ui.screens.settings.excluded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
@HiltViewModel
class ExcludedSongsViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {
    val uiState: StateFlow<ExcludedSongsUiState> = songRepository.getSongs(SongQuery.All(includeExcluded = true))
        .map { songs ->
            ExcludedSongsUiState(
                songs = songs.orEmpty().filter { it.blacklisted }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() }),
                loading = songs == null
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExcludedSongsUiState())

    // TODO(#389): switch to the ExcludeSongs use case (MediaAction.Include) once it lands on main
    fun onInclude(song: Song) {
        viewModelScope.launch { songRepository.setExcluded(listOf(song), false) }
    }

    fun onIncludeAll() {
        viewModelScope.launch { songRepository.clearExcludeList() }
    }
}
