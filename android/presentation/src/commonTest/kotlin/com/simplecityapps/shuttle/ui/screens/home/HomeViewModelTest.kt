package com.simplecityapps.shuttle.ui.screens.home

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.platform.AppVersion
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var preferenceManager: GeneralPreferenceManager
    private lateinit var settingsStore: SettingsStore
    private lateinit var analyticsConsentSettings: AnalyticsConsentSettings
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()
    private val queue = FakeQueueOperations()
    private val playback = FakePlaybackOperations()
    private val appVersion = AppVersion { VERSION_NAME }

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
        val sections = HomeSections(albums, FakeAlbumArtistRepository(), songs, seed = 1, dispatcher = testDispatcher)
        return HomeViewModel(
            sections,
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
    fun `a library shows its shelves`() = runTest(testDispatcher) {
        val often = createAlbum("Phase Garden", "Juniper Static", playCount = 5)
        songs.setSongs(listOf(chlorophyllLoop))
        albums.setAlbums(listOf(often))

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.mostPlayed shouldBe listOf(often)
        content.showWhatsNew shouldBe false
    }

    @Test
    fun `shuffle all shuffles every song`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop))

        viewModel().shuffleAll() shouldBe MediaAction.Shuffle(MediaSelection.Songs(listOf(chlorophyllLoop)))
    }

    @Test
    fun `shuffle all does nothing on an empty library`() = runTest(testDispatcher) {
        viewModel().shuffleAll().shouldBeNull()
    }

    @Test
    fun `unseen release notes show the whats new card until handled`() = runTest(testDispatcher) {
        preferenceManager.lastViewedChangelogVersion = "2020.01.01"
        songs.setSongs(listOf(chlorophyllLoop))
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
        songs.setSongs(listOf(chlorophyllLoop))

        (viewModel().uiState.value as HomeUiState.Content).showWhatsNew shouldBe false
    }

    @Test
    fun `the analytics notice shows once when the notice hasn't been shown yet`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop))

        val viewModel = viewModel()

        val content = viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.events.map { it.value } shouldBe listOf(HomeEvent.AnalyticsNowOn)
        analyticsConsentSettings.noticeShown.value shouldBe true
    }

    @Test
    fun `consuming the analytics notice event removes it`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop))
        val viewModel = viewModel()
        val pending = (viewModel.uiState.value as HomeUiState.Content).events.single()

        viewModel.onEventHandled(pending.id)
        runCurrent()

        (viewModel.uiState.value as HomeUiState.Content).events.shouldBeEmpty()
    }

    @Test
    fun `the analytics notice does not show once already marked shown`() = runTest(testDispatcher) {
        analyticsConsentSettings.noticeShown.value = true
        songs.setSongs(listOf(chlorophyllLoop))

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.events.shouldBeEmpty()
    }

    @Test
    fun `no queue - no resume hero`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop))

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume.shouldBeNull()
    }

    @Test
    fun `the resume hero offers the queue from its current song - with the time left in it`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop, tidalMoss))
        queueOf(listOf(chlorophyllLoop, tidalMoss), current = 1)
        playback.progressFlow.value = PlaybackProgress(position = 65_400, duration = 200_000)

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume shouldBe
            ResumeQueue(song = tidalMoss, songs = listOf(chlorophyllLoop, tidalMoss), timeLeftMs = 134_000, playing = false)
    }

    @Test
    fun `before any progress - the time left counts from where the song was left`() = runTest(testDispatcher) {
        songs.setSongs(listOf(tidalMoss))
        queueOf(listOf(tidalMoss), current = 0)

        viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume?.timeLeftMs shouldBe 170_000
    }

    @Test
    fun `the resume hero follows playback and toggles it`() = runTest(testDispatcher) {
        songs.setSongs(listOf(tidalMoss))
        queueOf(listOf(tidalMoss), current = 0)
        playback.playbackStateFlow.value = PlaybackState.Playing
        val viewModel = viewModel()

        viewModel.uiState.value.shouldBeInstanceOf<HomeUiState.Content>().resume?.playing shouldBe true
        viewModel.onTogglePlayback()

        playback.calls shouldBe listOf("togglePlayback()")
    }

    @Test
    fun `shuffling the resume hero shuffles the queue's songs`() = runTest(testDispatcher) {
        songs.setSongs(listOf(chlorophyllLoop, tidalMoss))
        queueOf(listOf(chlorophyllLoop, tidalMoss), current = 0)

        viewModel().shuffleQueue() shouldBe MediaAction.Shuffle(MediaSelection.Songs(listOf(chlorophyllLoop, tidalMoss)))
    }

    private companion object {
        const val VERSION_NAME = "2026.09.27"
    }
}
