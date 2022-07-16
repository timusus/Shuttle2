package com.simplecityapps.shuttle.ui.library

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.GetGenres
import com.simplecityapps.shuttle.model.GetSongs
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.ViewModel
import kotlinx.coroutines.flow.*

@HiltViewModel
class ArtistListViewModel @Inject constructor(getSongs: GetSongs) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    init {
        getSongs()
            .map { songs -> songs.toArtistViews() }
            .onEach { songs -> _viewState.emit(ViewState.Ready(songs)) }
            .launchIn(coroutineScope)
    }

    sealed class ViewState {
        object Loading : ViewState()
        data class Ready(val artists: List<ArtistView>) : ViewState()
    }
}

class ArtistView(
    val title: String,
    val songs: List<Song>
)

fun List<Song>.toArtistViews(): List<ArtistView> {
    return groupBy { it.artists }
        .map { (artists, songs) ->
            ArtistView(
                title = artists.joinToString { ", " },
                songs = songs
            )
        }
}