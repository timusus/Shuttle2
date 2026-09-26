package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
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
        /** The queue to pick up from, or null with none (#490). */
        val resume: ResumeQueue? = null,
        val events: List<PendingEvent<HomeEvent>> = emptyList(),
    ) : HomeUiState
}

/** An event Home's UI must handle once, whose loss would be a bug. */
enum class HomeEvent {
    /** Analytics just turned on for this upgrader who never chose (#481); shown once. */
    AnalyticsNowOn,
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    homeSections: HomeSections,
    private val isWhatsNewPending: IsWhatsNewPending,
    private val markChangelogViewed: MarkChangelogViewed,
    private val readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    observeResumeQueue: ObserveResumeQueue,
    private val togglePlayback: TogglePlayback,
) : ViewModel() {
    private val whatsNewPending = MutableStateFlow(isWhatsNewPending())
    private val events = PendingEvents<HomeEvent>()

    init {
        if (!readSetting(AnalyticsConsentSettings.NoticeShown)) {
            saveSetting(AnalyticsConsentSettings.NoticeShown, true)
            events.post(HomeEvent.AnalyticsNowOn)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(homeSections(), observeResumeQueue(), whatsNewPending, events.flow) { sections, resume, whatsNew, pendingEvents ->
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
                resume = resume,
                events = pendingEvents,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Shuffles the whole library, or null before it has loaded. */
    fun shuffleAll(): MediaAction? = (uiState.value as? HomeUiState.Content)?.let { MediaAction.Shuffle(MediaSelection.Songs(it.songs)) }

    /** Shuffles the queue the resume hero offers, or null with none. */
    fun shuffleQueue(): MediaAction? = (uiState.value as? HomeUiState.Content)?.resume?.let { MediaAction.Shuffle(MediaSelection.Songs(it.songs)) }

    fun onTogglePlayback() = togglePlayback()

    /** Opening the changelog or dismissing the card marks this version's notes as seen. */
    fun onWhatsNewHandled() {
        markChangelogViewed()
        whatsNewPending.value = false
    }

    fun onEventHandled(id: Long) = events.consume(id)
}
