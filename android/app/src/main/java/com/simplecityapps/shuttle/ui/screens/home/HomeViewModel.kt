package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** The library has no songs; Home is where the empty state lives. */
    data object Empty : HomeUiState

    data class Content(
        val showWhatsNew: Boolean,
        val recentlyPlayed: List<Album>,
        val recentlyAdded: List<Album>,
        val mostPlayed: List<Album>,
        val somethingDifferent: List<AlbumArtist>,
        val songs: List<Song>,
    ) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    homeSections: HomeSections,
    private val preferenceManager: GeneralPreferenceManager,
) : ViewModel() {
    private val whatsNewPending = MutableStateFlow(isWhatsNewPending())

    val uiState: StateFlow<HomeUiState> = combine(homeSections(), whatsNewPending) { sections, whatsNew ->
        if (sections.songs.isEmpty()) {
            HomeUiState.Empty
        } else {
            HomeUiState.Content(
                showWhatsNew = whatsNew,
                recentlyPlayed = sections.recentlyPlayed,
                recentlyAdded = sections.recentlyAdded,
                mostPlayed = sections.mostPlayed,
                somethingDifferent = sections.somethingDifferent,
                songs = sections.songs,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Shuffles the whole library, or null before it has loaded. */
    fun shuffleAll(): MediaAction? = (uiState.value as? HomeUiState.Content)?.let { MediaAction.Shuffle(MediaSelection.Songs(it.songs)) }

    /** Opening the changelog or dismissing the card marks this version's notes as seen. */
    fun onWhatsNewHandled() {
        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
        whatsNewPending.value = false
    }

    private fun isWhatsNewPending(): Boolean = preferenceManager.showChangelogOnLaunch && preferenceManager.lastViewedChangelogVersion != BuildConfig.VERSION_NAME
}
