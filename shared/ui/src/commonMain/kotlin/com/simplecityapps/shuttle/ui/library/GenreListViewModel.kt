package com.simplecityapps.shuttle.ui.library

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.GetGenres
import com.simplecityapps.shuttle.ui.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@HiltViewModel
class GenreListViewModel @Inject constructor(getGenres: GetGenres) : ViewModel() {

    private val _viewState = MutableStateFlow<ViewState>(ViewState.Loading)
    val viewState = _viewState.asStateFlow()

    init {
        getGenres()
            .onEach { genres ->
                _viewState.emit(ViewState.Ready(genres))
            }
            .launchIn(coroutineScope)
    }

    sealed class ViewState {
        object Loading : ViewState()
        data class Ready(val genres: List<Genre>) : ViewState()
    }
}