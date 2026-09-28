package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.playContext
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.platform.AppVersion
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.AnalyticsConsentSettings
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.screens.settings.about.IsWhatsNewPending
import com.simplecityapps.shuttle.ui.screens.settings.about.MarkChangelogViewed
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var preferenceManager: GeneralPreferenceManager
    private lateinit var settingsStore: SettingsStore
    private lateinit var analyticsConsentSettings: AnalyticsConsentSettings
    private val suggestions = FakeSuggestionsRepository()
    private val playHistory = FakePlayHistoryRepository()
    private val queue = FakeQueueOperations()
    private val playback = FakePlaybackOperations()
    private val appVersion = AppVersion { VERSION_NAME }

    private val start = Instant.parse("2026-09-23T08:30:00Z")
    private val phaseGarden = createAlbum("phase garden", "juniper static")
    private val dustChoir = createAlbum("dust choir", "juniper static")
    private val saltMarsh = createAlbum("salt marsh", "juniper static")

    init {
        suggestions.albums = listOf(phaseGarden, dustChoir)
    }

    private val twoRecentContexts get() = listOf(RecentContext(phaseGarden.playContext, start), RecentContext(dustChoir.playContext, start))

    private val chlorophyllLoop = createSong(id = 1, name = "Chlorophyll Loop", albumArtist = "Juniper Static", album = "Phase Garden")
    private val tidalMoss = createSong(id = 2, name = "Tidal Moss", albumArtist = "Juniper Static", album = "Phase Garden", duration = 200_000).copy(playbackPosition = 30_000)

    /** Puts [songs] in the queue with the one at [current] playing. */
    private fun queueOf(songs: List<Song>, current: Int) {
        val items = songs.mapIndexed { index, song -> song.toQueueItem(isCurrent = index == current) }
        queue.queueStateFlow.value = QueueState(items = items, currentItem = items[current], currentPosition = current, isRestored = true)
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferenceManager = GeneralPreferenceManager(InMemoryKeyValueStore())
        preferenceManager.lastViewedChangelogVersion = VERSION_NAME
        settingsStore = SettingsStore(InMemoryKeyValueStore())
        analyticsConsentSettings = AnalyticsConsentSettings(settingsStore)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): HomeViewModel {
        // Wall time moves with the test's virtual time, from 8:30am
        val clock = object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
        val homeTime = HomeTime(clock) { TimeZone.UTC }
        val resolve = ResolveHomeItems(suggestions, FakePlaylistRepository())
        val load = LoadHomeSections(
            JumpBackIn(playHistory, suggestions, resolve),
            AroundThisTime(playHistory, resolve),
            OnRepeat(playHistory, resolve),
            Rediscover(suggestions, resolve),
            RecentlyAdded(suggestions, resolve),
            GenrePicks(playHistory, suggestions),
            homeTime,
        )
        return HomeViewModel(
            ObserveHomeSections(suggestions, playHistory, load, homeTime, testDispatcher),
            IsWhatsNewPending(preferenceManager, appVersion),
            MarkChangelogViewed(preferenceManager, appVersion),
            ReadSetting(settingsStore),
            SaveSetting(settingsStore),
            ObserveResumeQueue(queue, playback),
            TogglePlayback(playback),
        ).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
        }
    }

    @Test
    fun `an empty library is the empty state`() = runTest(testDispatcher) {
        viewModel().uiState.value shouldBe HomeUiState.Empty
    }

    @Test
    fun `a library with history shows its sections`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        playHistory.eventCount.value = 1
        playHistory.recentContexts = twoRecentContexts

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.sections shouldBe listOf(HomeSection(HomeSectionId.JumpBackIn, HomeSectionTitle.JumpBackIn, listOf(HomeItem.AlbumItem(phaseGarden), HomeItem.AlbumItem(dustChoir))))
        content.showWhatsNew shouldBe false
    }

    @Test
    fun `a library never played shows the cold start sections`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.map { it.id } shouldBe listOf(HomeSectionId.ShuffleAll)
    }

    @Test
    fun `history changes reload the sections once they settle`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        val viewModel = viewModel()
        playHistory.recentContexts = twoRecentContexts

        playHistory.eventCount.value = 1
        runCurrent()
        playHistory.eventCount.value = 2
        advanceTimeBy(ObserveHomeSections.DEBOUNCE - 1.milliseconds)
        runCurrent()
        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.map { it.id } shouldBe listOf(HomeSectionId.ShuffleAll)

        advanceTimeBy(2.milliseconds)
        runCurrent()
        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.map { it.id } shouldBe listOf(HomeSectionId.JumpBackIn)
    }

    @Test
    fun `the hour turning reloads the sections`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        playHistory.eventCount.value = 1
        playHistory.contextsAroundHour = listOf(phaseGarden, dustChoir, saltMarsh).map { ContextDays(it.playContext, days = 3, weekendDays = 0, lastPlayedAt = start) }
        suggestions.albums = listOf(phaseGarden, dustChoir, saltMarsh)
        val viewModel = viewModel()
        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.single().title shouldBe HomeSectionTitle.ThisMorning

        // 8:30am to 12:00pm: the ticker turns at 9, 10, 11 and 12
        advanceTimeBy(3.5.hours + ObserveHomeSections.DEBOUNCE)
        runCurrent()

        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.single().title shouldBe HomeSectionTitle.ThisAfternoon
    }

    @Test
    fun `shuffle all shuffles the library by query`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2

        viewModel().shuffleAll() shouldBe MediaAction.Shuffle(MediaSelection.SongsMatching(SongQuery.All()))
    }

    @Test
    fun `shuffle all does nothing on an empty library`() = runTest(testDispatcher) {
        viewModel().shuffleAll().shouldBeNull()
    }

    @Test
    fun `unseen release notes show the whats new card until handled`() = runTest(testDispatcher) {
        preferenceManager.lastViewedChangelogVersion = "2020.01.01"
        suggestions.songCount.value = 1
        val viewModel = viewModel()
        (viewModel.uiState.value as HomeUiState.Content).showWhatsNew shouldBe true

        viewModel.onWhatsNewHandled()
        runCurrent()

        (viewModel.uiState.value as HomeUiState.Content).showWhatsNew shouldBe false
        preferenceManager.lastViewedChangelogVersion shouldBe VERSION_NAME
    }

    @Test
    fun `the whats new card stays hidden when changelogs are turned off`() = runTest(testDispatcher) {
        preferenceManager.lastViewedChangelogVersion = "2020.01.01"
        preferenceManager.showChangelogOnLaunch = false
        suggestions.songCount.value = 1

        (viewModel().uiState.value as HomeUiState.Content).showWhatsNew shouldBe false
    }

    @Test
    fun `the analytics notice shows once when the notice hasn't been shown yet`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1

        val viewModel = viewModel()

        val content = viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.events.map { it.value } shouldBe listOf(HomeEvent.AnalyticsNowOn)
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `consuming the analytics notice event removes it`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1
        val viewModel = viewModel()
        val pending = (viewModel.uiState.value as HomeUiState.Content).events.single()

        viewModel.onEventHandled(pending.id)
        runCurrent()

        (viewModel.uiState.value as HomeUiState.Content).events.shouldBeEmpty()
    }

    @Test
    fun `the analytics notice does not show once already marked shown`() = runTest(testDispatcher) {
        analyticsConsentSettings.noticeShown.value = true
        suggestions.songCount.value = 1

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.events.shouldBeEmpty()
    }

    @Test
    fun `no queue - no resume hero`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume.shouldBeNull()
    }

    @Test
    fun `the resume hero offers the queue from its current song - with the time left in it`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1
        queueOf(listOf(chlorophyllLoop, tidalMoss), current = 1)
        playback.progressFlow.value = PlaybackProgress(position = 65_400, duration = 200_000)

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume shouldBe
            ResumeQueue(song = tidalMoss, songs = listOf(chlorophyllLoop, tidalMoss), timeLeftMs = 134_000, playing = false)
    }

    @Test
    fun `before any progress - the time left counts from where the song was left`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1
        queueOf(listOf(tidalMoss), current = 0)

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume?.timeLeftMs shouldBe 170_000
    }

    @Test
    fun `the resume hero follows playback and toggles it`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1
        queueOf(listOf(tidalMoss), current = 0)
        playback.playbackStateFlow.value = PlaybackState.Playing
        val viewModel = viewModel()

        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume?.playing shouldBe true
        viewModel.onTogglePlayback()

        playback.calls shouldBe listOf("togglePlayback()")
    }

    @Test
    fun `shuffling the resume hero shuffles the queue's songs`() = runTest(testDispatcher) {
        suggestions.songCount.value = 1
        queueOf(listOf(chlorophyllLoop, tidalMoss), current = 0)

        viewModel().shuffleQueue() shouldBe MediaAction.Shuffle(MediaSelection.Songs(listOf(chlorophyllLoop, tidalMoss)))
    }

    private companion object {
        const val VERSION_NAME = "2026.09.27"
    }
}
