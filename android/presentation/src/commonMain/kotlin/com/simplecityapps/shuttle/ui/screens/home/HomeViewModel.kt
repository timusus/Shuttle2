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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** The library has no songs; Home is where the empty state lives. */
    data object Empty : HomeUiState

    data class Content(
        val showWhatsNew: Boolean,
        /** The suggestion sections, in order; empty ones are left out (#633). */
        val sections: List<HomeSection>,
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
    loadHomeCovers: LoadHomeCovers,
) : ViewModel() {
    private val whatsNewPending = MutableStateFlow(isWhatsNewPending())
    private val events = PendingEvents<HomeEvent>()
    private val visible = MutableStateFlow(false)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        if (!readSetting(AnalyticsConsentSettings.NoticeShown)) {
            saveSetting(AnalyticsConsentSettings.NoticeShown, true)
            events.post(HomeEvent.AnalyticsNowOn)
        }
    }

    /**
     * The sections with their mosaics' covers; the sections show first and the covers follow as they load. A reload
     * keeps the covers it already has for the items still shown, so their mosaics don't blank and refill.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sectionsWithCovers = observeHomeSections(visible, refreshes).runningFold(null as SectionsWithCovers?) { previous, sections ->
        val keys = sections.orEmpty().flatMap { section -> section.items.map { it.key } }.toSet()
        SectionsWithCovers(sections, previous?.covers.orEmpty().filterKeys { it in keys })
    }.filterNotNull().flatMapLatest { held ->
        flow { emit(held.copy(covers = held.sections?.let { loadHomeCovers(it) }.orEmpty())) }.onStart { emit(held) }
    }

    val uiState: StateFlow<HomeUiState> = combine(sectionsWithCovers, whatsNewPending, events.flow) { (sections, covers), whatsNew, pendingEvents ->
        if (sections == null) {
            HomeUiState.Empty
        } else {
            HomeUiState.Content(
                showWhatsNew = whatsNew,
                sections = sections,
                events = pendingEvents,
                covers = covers,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Shuffles the whole library, resolved as it plays; null before the library has loaded or while it's empty. */
    fun shuffleAll(): MediaAction? = (uiState.value as? HomeUiState.Content)?.let { MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All())) }

    /** Opening the changelog or dismissing the card marks this version's notes as seen. */
    fun onWhatsNewHandled() {
        markChangelogViewed()
        whatsNewPending.value = false
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /**
     * Whether Home is on screen: its tab is showing and the app is in the foreground. Becoming visible reloads the
     * sections; while hidden they reload only as the hour turns (#672).
     */
    fun onVisibilityChanged(visible: Boolean) {
        this.visible.value = visible
    }

    /** Pull to refresh: reloads the sections now. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private data class SectionsWithCovers(
        val sections: List<HomeSection>?,
        val covers: Map<String, List<Song>>,
    )
}
