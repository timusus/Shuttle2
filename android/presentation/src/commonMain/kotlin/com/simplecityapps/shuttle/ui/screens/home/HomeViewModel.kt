package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.settings.about.IsWhatsNewPending
import com.simplecityapps.shuttle.ui.screens.settings.about.MarkChangelogViewed
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** The library has no songs; Home is where the empty state lives. */
    data object Empty : HomeUiState

    data class Content(
        val showWhatsNew: Boolean,
        /** The suggestion sections, in order; empty ones are left out (#633). */
        val sections: List<HomeSection>,
        /** The queue to pick up from, or null with none (#490). */
        val resume: ResumeQueue? = null,
        val events: List<PendingEvent<HomeEvent>> = emptyList(),
        /** Each playlist and genre item's mosaic covers by [HomeItem.key], once loaded; absent for one with none (#646). */
        val covers: Map<String, List<Song>> = emptyMap(),
    ) : HomeUiState
}

/** An event Home's UI must handle once, whose loss would be a bug. */
enum class HomeEvent {
    /** Analytics just turned on for this upgrader who never chose (#481); shown once. */
    AnalyticsNowOn,
}

@ViewModelKey(HomeViewModel::class)
@ContributesIntoMap(AppScope::class)
class HomeViewModel @Inject constructor(
    observeHomeSections: ObserveHomeSections,
    private val isWhatsNewPending: IsWhatsNewPending,
    private val markChangelogViewed: MarkChangelogViewed,
    private val readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    observeResumeQueue: ObserveResumeQueue,
    private val togglePlayback: TogglePlayback,
    loadHomeCovers: LoadHomeCovers,
) : ViewModel() {
    private val whatsNewPending = MutableStateFlow(isWhatsNewPending())
    private val events = PendingEvents<HomeEvent>()

    init {
        if (!readSetting(AnalyticsConsentSettings.NoticeShown)) {
            saveSetting(AnalyticsConsentSettings.NoticeShown, true)
            events.post(HomeEvent.AnalyticsNowOn)
        }
    }

    /** The sections with their mosaics' covers; the sections show first and the covers follow as they load. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sectionsWithCovers = observeHomeSections().flatMapLatest { sections ->
        flow { emit(sections to sections?.let { loadHomeCovers(it) }.orEmpty()) }.onStart { emit(sections to emptyMap()) }
    }

    val uiState: StateFlow<HomeUiState> = combine(sectionsWithCovers, observeResumeQueue(), whatsNewPending, events.flow) { (sections, covers), resume, whatsNew, pendingEvents ->
        if (sections == null) {
            HomeUiState.Empty
        } else {
            HomeUiState.Content(
                showWhatsNew = whatsNew,
                sections = sections,
                resume = resume,
                events = pendingEvents,
                covers = covers,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Shuffles the whole library, resolved as it plays; null before the library has loaded or while it's empty. */
    fun shuffleAll(): MediaAction? = (uiState.value as? HomeUiState.Content)?.let { MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All())) }

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
