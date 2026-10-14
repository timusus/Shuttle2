package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.PrivacySettings
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState

    /** The library has no songs; Home is where the empty state lives. */
    data class Empty(
        /** As [Content.showTelemetryNotice]: a fresh install usually has no library yet. */
        val showTelemetryNotice: Boolean,
    ) : HomeUiState

    data class Content(
        val showWhatsNew: Boolean,
        /** Crash reports and usage data are on and the user hasn't yet acknowledged being told (#1031). */
        val showTelemetryNotice: Boolean = false,
        /** The suggestion sections, in order; empty ones are left out (#633). */
        val sections: List<HomeSection>,
        val events: List<PendingEvent<HomeEvent>> = emptyList(),
        /** Each playlist and genre item's mosaic covers by [HomeItem.key], once loaded; absent for one with none (#646). */
        val covers: Map<String, List<Song>> = emptyMap(),
        /** A pull to refresh is waiting on its reload. */
        val refreshing: Boolean = false,
    ) : HomeUiState
}

/** An event Home's UI must handle once, whose loss would be a bug. */
sealed interface HomeEvent {
    /** A shelf's songs are resolved: the UI hands [action] to the media actions to play them. */
    data class PlayShelf(val action: MediaAction) : HomeEvent

    /** A random artist, genre or year was picked: the UI hands [action] to the media actions and names [label] to the user. */
    data class ShuffleRandom(val label: String, val action: MediaAction) : HomeEvent

    /** A random shuffle found no artist, genre or year to pick: the UI tells the user. */
    data object NothingToShuffle : HomeEvent
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
    private val playHomeSection: PlayHomeSection,
    private val pickRandomShuffle: PickRandomShuffle,
) : ViewModel() {
    private val whatsNewPending = MutableStateFlow(isWhatsNewPending())
    private val events = PendingEvents<HomeEvent>()
    private val visible = MutableStateFlow(false)
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val refreshing = MutableStateFlow(false)
    private val resolvingSections = mutableSetOf<HomeSectionId>()
    private var pickingRandom = false

    // Counts as seen only once the user taps OK or Settings, so a missed card comes back next launch (#1031).
    private val telemetryNoticePending = MutableStateFlow(
        !readSetting(AnalyticsConsentSettings.NoticeShown) &&
            (readSetting(PrivacySettings.CrashReporting) || readSetting(PrivacySettings.Analytics)),
    )

    /**
     * The covers last loaded, by [HomeItem.key]. Held by the view model rather than the flow so they outlive a
     * resubscription as well as a reload.
     */
    private var loadedCovers: Map<String, List<Song>> = emptyMap()

    /**
     * The sections with their mosaics' covers; the sections show first and the covers follow as they load. A reload
     * keeps the covers it already has for the items still shown, so their mosaics don't blank and refill. While the
     * first load brings its sections in one at a time, each new set only extends the last, so the cover load under way
     * carries on and a second one loads just the new sections' items, rather than restarting for every set.
     */
    private val sectionsWithCovers = channelFlow {
        var latest: List<HomeSection>? = null
        var loadedFor: List<HomeSection> = emptyList()
        // The cover loads still running for this run of sets; a restart cancels and joins them so none writes after it.
        var coverLoads = emptyList<Job>()
        // The covers the loads of one run of extending sets have found; each load adds to it, then it becomes [loadedCovers].
        var fresh = emptyMap<String, List<Song>>()

        fun current() = SectionsWithCovers(latest, loadedCovers.filterKeys { key -> latest.orEmpty().any { section -> section.items.any { it.key == key } } })

        observeHomeSections(visible, refreshes).collect { sections ->
            refreshing.value = false
            val extension = sections != null && coverLoads.any { it.isActive } &&
                sections.size >= loadedFor.size && sections.take(loadedFor.size) == loadedFor
            val toLoad = if (extension) sections.drop(loadedFor.size) else sections.orEmpty()
            if (!extension) {
                coverLoads.forEach { it.cancelAndJoin() }
                fresh = emptyMap()
            }
            latest = sections
            loadedFor = sections.orEmpty()
            send(current())
            if (sections != null) {
                coverLoads = coverLoads.filter { it.isActive } + launch {
                    val loaded = loadHomeCovers(toLoad)
                    // Merge into what the loads that finished meanwhile found, so overlapping loads keep each other's covers.
                    fresh = fresh + loaded
                    loadedCovers = fresh
                    send(current())
                }
            }
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(sectionsWithCovers, whatsNewPending, events.flow, refreshing, telemetryNoticePending) { (sections, covers), whatsNew, pendingEvents, refreshing, telemetryNotice ->
        if (sections == null) {
            HomeUiState.Empty(showTelemetryNotice = telemetryNotice)
        } else {
            HomeUiState.Content(
                showWhatsNew = whatsNew,
                showTelemetryNotice = telemetryNotice,
                sections = sections,
                events = pendingEvents,
                covers = covers,
                refreshing = refreshing,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Shuffles the whole library, resolved as it plays; null before the library has loaded or while it's empty. */
    fun shuffleAll(): MediaAction? = (uiState.value as? HomeUiState.Content)?.let { MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All())) }

    /**
     * Shuffles a random artist, genre or year, posting [HomeEvent.ShuffleRandom] once picked, or
     * [HomeEvent.NothingToShuffle] if the library has none. Ignored while an earlier pick is still resolving.
     */
    fun shuffleRandom(by: ShuffleBy) {
        if (uiState.value !is HomeUiState.Content || pickingRandom) return
        pickingRandom = true
        viewModelScope.launch {
            try {
                val pick = pickRandomShuffle(by)
                events.post(if (pick == null) HomeEvent.NothingToShuffle else HomeEvent.ShuffleRandom(pick.label, pick.action))
            } finally {
                pickingRandom = false
            }
        }
    }

    /**
     * Plays the whole shelf of [id] in order, posting [HomeEvent.PlayShelf] once its songs are resolved. Does nothing
     * when it's not on screen, isn't [HomeSection.playable] or has no songs, or while the same shelf is still resolving.
     */
    fun playSection(id: HomeSectionId) {
        val section = (uiState.value as? HomeUiState.Content)?.sections?.firstOrNull { it.id == id }?.takeIf { it.playable } ?: return
        if (!resolvingSections.add(id)) return
        viewModelScope.launch {
            try {
                playHomeSection(section)?.let { events.post(HomeEvent.PlayShelf(it)) }
            } finally {
                resolvingSections.remove(id)
            }
        }
    }

    /** Opening the changelog or dismissing the card marks this version's notes as seen. */
    fun onWhatsNewHandled() {
        markChangelogViewed()
        whatsNewPending.value = false
    }

    fun onTelemetryNoticeAcknowledged() {
        saveSetting(AnalyticsConsentSettings.NoticeShown, true)
        telemetryNoticePending.value = false
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /**
     * Whether Home is on screen: its tab is showing and the app is in the foreground. Becoming visible reloads the
     * sections; while hidden they reload only as the hour turns (#672).
     */
    fun onVisibilityChanged(visible: Boolean) {
        this.visible.value = visible
    }

    /** Pull to refresh: reloads the sections now; [HomeUiState.Content.refreshing] until they're back. */
    fun refresh() {
        refreshing.value = true
        refreshes.tryEmit(Unit)
    }

    private data class SectionsWithCovers(
        val sections: List<HomeSection>?,
        val covers: Map<String, List<Song>>,
    )
}
