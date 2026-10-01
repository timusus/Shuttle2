package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createGenre
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlayHistoryRepository
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSuggestionsRepository
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.mediaprovider.repository.playhistory.ContextDays
import com.simplecityapps.mediaprovider.repository.playhistory.RecentContext
import com.simplecityapps.mediaprovider.repository.playhistory.ResumePoint
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
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
import com.simplecityapps.shuttle.ui.text.StringKey
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
import kotlin.time.Duration.Companion.seconds
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
    private val importState = FakeSongImportStateProvider()
    private val genres = FakeGenreRepository()
    private val appVersion = AppVersion { VERSION_NAME }

    private val start = Instant.parse("2026-09-23T08:30:00Z")
    private val phaseGarden = createAlbum("phase garden", "juniper static")
    private val dustChoir = createAlbum("dust choir", "juniper static")
    private val saltMarsh = createAlbum("salt marsh", "juniper static")

    init {
        suggestions.albums = listOf(phaseGarden, dustChoir)
    }

    private val twoRecentContexts get() = listOf(RecentContext(phaseGarden.playContext, start), RecentContext(dustChoir.playContext, start))

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

    /** Home's view model, collected, and on screen unless [visible] is false. */
    private fun TestScope.viewModel(visible: Boolean = true): HomeViewModel {
        // Wall time moves with the test's virtual time, from 8:30am
        val clock = object : Clock {
            override fun now(): Instant = start + testScheduler.currentTime.milliseconds
        }
        val homeTime = HomeTime(clock) { TimeZone.UTC }
        val resolve = ResolveHomeItems(suggestions, FakePlaylistRepository())
        val load = LoadHomeSections(
            JumpBackIn(playHistory, suggestions, resolve),
            AroundThisTime(playHistory, resolve),
            HeavyRotation(playHistory, resolve),
            Rediscover(suggestions, resolve),
            RecentlyAdded(suggestions, resolve),
            GenrePicks(playHistory, suggestions),
            playHistory,
            homeTime,
        )
        return HomeViewModel(
            ObserveHomeSections(suggestions, playHistory, importState, load, homeTime, testDispatcher),
            IsWhatsNewPending(preferenceManager, appVersion),
            MarkChangelogViewed(preferenceManager, appVersion),
            ReadSetting(settingsStore),
            SaveSetting(settingsStore),
            LoadHomeCovers(FakePlaylistRepository(), genres),
        ).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            viewModel.onVisibilityChanged(visible)
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
        content.sections shouldBe listOf(HomeSection(HomeSectionId.JumpBackIn, HomeSectionTitle.JumpBackIn, StringKey.HOME_JUMP_BACK_IN_SUBTITLE, listOf(HomeItem.AlbumItem(phaseGarden), HomeItem.AlbumItem(dustChoir))))
        content.showWhatsNew shouldBe false
    }

    @Test
    fun `jump back in says where each item's queue was left, and which played through`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        playHistory.eventCount.value = 1
        playHistory.recentContexts = twoRecentContexts
        playHistory.resumePoints[phaseGarden.playContext] = resumePoint(phaseGarden.playContext, track = 4, trackCount = 12, songName = "Glasshouse", songDurationMs = 60_000)
        playHistory.resumePoints[dustChoir.playContext] = resumePoint(dustChoir.playContext, track = 9, trackCount = 10, finished = true)

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()

        val jumpBackIn = content.sections.single { it.id == HomeSectionId.JumpBackIn }
        jumpBackIn.progress shouldBe mapOf(
            HomeItem.AlbumItem(phaseGarden).key to HomeItemProgress("Glasshouse", 30_000, 4.5f / 12, shuffled = false, finished = false, updatedAt = start),
            HomeItem.AlbumItem(dustChoir).key to HomeItemProgress(null, 30_000, 1f, shuffled = false, finished = true, updatedAt = start),
        )
    }

    @Test
    fun `an item's progress is its song's place in the queue, without the song's own share when its length is unknown`() {
        HomeItemProgress.of(resumePoint(phaseGarden.playContext, track = 3, trackCount = 4)).fraction shouldBe 0.75f
        HomeItemProgress.of(resumePoint(phaseGarden.playContext, track = 7, trackCount = 4, songDurationMs = 60_000)).fraction shouldBe 0.875f
        HomeItemProgress.of(resumePoint(phaseGarden.playContext, track = 0, trackCount = 0)).fraction shouldBe 0f
        HomeItemProgress.of(resumePoint(phaseGarden.playContext, track = 1, trackCount = 2, shuffled = true)).shuffled shouldBe true
    }

    @Test
    fun `a jump back in item resumes, falling back to its play action`() {
        val item = HomeItem.AlbumItem(phaseGarden)

        item.resumeAction() shouldBe MediaAction.Resume(item.playAction(), phaseGarden.playContext)
    }

    @Test
    fun `a library never played shows the cold start sections`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.map { it.id } shouldBe listOf(HomeSectionId.ShuffleAll)
    }

    private val HomeViewModel.sectionIds get() = uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.map { it.id }

    /** A library played once, whose Jump back in appears only once Home reloads. */
    private fun TestScope.playedLibrary(visible: Boolean = true): HomeViewModel {
        suggestions.songCount.value = 2
        val viewModel = viewModel(visible)
        playHistory.recentContexts = twoRecentContexts
        playHistory.eventCount.value = 1
        runCurrent()
        return viewModel
    }

    @Test
    fun `nothing loads until home is first on screen`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        val viewModel = viewModel(visible = false)
        viewModel.uiState.value shouldBe HomeUiState.Loading

        viewModel.onVisibilityChanged(true)
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.ShuffleAll)
    }

    @Test
    fun `plays don't reload the sections while home is on screen (#672)`() = runTest(testDispatcher) {
        val viewModel = playedLibrary()
        advanceTimeBy(1.hours)
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.ShuffleAll)
    }

    @Test
    fun `returning to home reloads the sections`() = runTest(testDispatcher) {
        val viewModel = playedLibrary()

        viewModel.onVisibilityChanged(false)
        runCurrent()
        viewModel.sectionIds shouldBe listOf(HomeSectionId.ShuffleAll)
        viewModel.onVisibilityChanged(true)
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.JumpBackIn)
    }

    @Test
    fun `pull to refresh reloads the sections`() = runTest(testDispatcher) {
        val viewModel = playedLibrary()
        val refreshing = mutableListOf<Boolean>()
        backgroundScope.launch { viewModel.uiState.collect { (it as? HomeUiState.Content)?.let { content -> refreshing += content.refreshing } } }
        runCurrent()

        viewModel.refresh()
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.JumpBackIn)
        // Refreshing from the pull until the reload is back
        refreshing shouldBe listOf(false, true, false)
    }

    @Test
    fun `a reload keeps the covers already loaded for the items still shown`() = runTest(testDispatcher) {
        // A cold start library whose genre picks are its largest genres, each with a cover
        suggestions.songCount.value = 2
        suggestions.genres = (1..GENRE_PICKS_MIN).map { createGenre("genre $it", songCount = GenrePicks.MIN_SONGS) }
        suggestions.genres.forEach { genres.setSongsForGenre(it.name, listOf(createSong(album = it.name))) }
        genres.coverDelay = 100.milliseconds
        val viewModel = viewModel()
        advanceTimeBy(1.seconds)
        runCurrent()
        val covers = viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().covers
        covers.keys shouldBe suggestions.genres.map { HomeItem.GenreItem(it).key }.toSet()

        viewModel.refresh()
        runCurrent()

        // Reloaded, with the covers still loading: the mosaics keep what they had
        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().covers shouldBe covers
    }

    @Test
    fun `an import completing reloads the sections while home is on screen`() = runTest(testDispatcher) {
        val viewModel = playedLibrary()

        importState.setState(importComplete())
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.JumpBackIn)
    }

    @Test
    fun `the library filling reloads home from its empty state`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        viewModel.uiState.value shouldBe HomeUiState.Empty

        suggestions.songCount.value = 2
        runCurrent()

        viewModel.sectionIds shouldBe listOf(HomeSectionId.ShuffleAll)
    }

    @Test
    fun `the hour turning reloads the sections only while home is hidden`() = runTest(testDispatcher) {
        suggestions.songCount.value = 2
        playHistory.eventCount.value = 1
        playHistory.contextsAroundHour = listOf(phaseGarden, dustChoir, saltMarsh).map { ContextDays(it.playContext, days = 3, weekendDays = 0, lastPlayedAt = start) }
        suggestions.albums = listOf(phaseGarden, dustChoir, saltMarsh)
        val viewModel = viewModel()
        val title = { viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().sections.single().title }
        title() shouldBe HomeSectionTitle.ThisMorning

        // 8:30am to 12:00pm: the hour turns at 9, 10, 11 and 12, but Home is on screen
        advanceTimeBy(3.5.hours)
        runCurrent()
        title() shouldBe HomeSectionTitle.ThisMorning

        viewModel.onVisibilityChanged(false)
        advanceTimeBy(1.hours)
        runCurrent()
        title() shouldBe HomeSectionTitle.ThisAfternoon
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

    private companion object {
        const val VERSION_NAME = "2026.09.27"
    }

    private fun resumePoint(
        context: PlayContext,
        track: Int,
        trackCount: Int,
        finished: Boolean = false,
        shuffled: Boolean = false,
        songName: String? = null,
        songDurationMs: Long? = null
    ) = ResumePoint(
        context,
        MediaProviderType.Shuttle,
        "/music/$track.flac",
        30_000,
        track,
        trackCount,
        shuffled = shuffled,
        finished = finished,
        updatedAt = start,
        songName = songName,
        songDurationMs = songDurationMs
    )
}
