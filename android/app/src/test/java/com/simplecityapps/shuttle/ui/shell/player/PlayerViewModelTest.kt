package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.clone
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val playbackManager = FakePlaybackManager()
    private val queueManager = FakeQueueManager()
    private val playlistRepository = FakePlaylistRepository()
    private val sleepTimerPreference = object : SleepTimerPreference {
        override var playToEnd: Boolean = false
    }
    private val replayGainPreference = object : ReplayGainPreference {
        override val mode = MutableStateFlow(ReplayGainMode.Off)

        override fun set(mode: ReplayGainMode) {
            this.mode.value = mode
        }
    }
    private val seededSongs = mutableListOf<Song>()
    private val seedSource = ArtworkSeedSource { song ->
        seededSongs += song
        ArtworkSeed.Available(Color.Red)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): PlayerViewModel {
        val sleepTimer = SleepTimer(playbackManager, backgroundScope, UnconfinedTestDispatcher(testScheduler)) { testScheduler.currentTime }
        val mediaActions = TestMediaActions(playlistRepository = playlistRepository, queueManager = queueManager, playbackManager = playbackManager)
        return PlayerViewModel(
            playbackOperations = playbackManager,
            queueOperations = queueManager,
            playlistRepository = playlistRepository,
            sleepTimer = sleepTimer,
            sleepTimerPreference = sleepTimerPreference,
            replayGainPreference = replayGainPreference,
            seedSource = seedSource,
            castAvailability = { false },
            clearQueue = ClearQueue(queueManager, playbackManager),
            restoreQueue = RestoreQueue(queueManager, playbackManager),
            availableMediaActions = AvailableMediaActions(mediaActions.resolveSongs, FakeSongDownloadRepository()),
            mediaActionHandler = mediaActions.handler,
            savedStateHandle = savedStateHandle,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.progress.collect {} }
        }
    }

    private fun songs(vararg names: String, album: String = "Album"): List<Song> = names.mapIndexed { index, name -> createSong(id = index.toLong(), name = name, album = album) }

    private fun queueOf(
        songs: List<Song>,
        current: Int = 0,
    ): QueueState {
        val items = songs.mapIndexed { index, song -> QueueItem(uid = 100L + index, song = song, isCurrent = index == current) }
        return QueueState.Empty.copy(items = items, currentItem = items.getOrNull(current), currentPosition = current, isRestored = true)
    }

    @Test
    fun `an unrestored empty queue is unknown, so the saved level stands`() {
        QueueState.Empty.toPlayerUiState().hasQueue shouldBe null
    }

    @Test
    fun `a restored empty queue has no queue`() {
        QueueState.Empty.copy(isRestored = true).toPlayerUiState().hasQueue shouldBe false
    }

    @Test
    fun `items map to songs marked played, current or upcoming`() {
        val state = queueOf(songs("One", "Two", "Three"), current = 1).toPlayerUiState()
        state.hasQueue shouldBe true
        state.items.map { it.title } shouldBe listOf("One", "Two", "Three")
        state.items.map { it.position } shouldBe listOf(QueuePosition.Played, QueuePosition.Current, QueuePosition.Upcoming)
        state.current?.title shouldBe "Two"
    }

    @Test
    fun `the state follows the queue, playback and modes`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        queueManager.shuffleModeFlow.value = QueueManager.ShuffleMode.On
        queueManager.repeatModeFlow.value = QueueManager.RepeatMode.One

        val state = viewModel.uiState.value
        state.current?.title shouldBe "One"
        state.playing shouldBe true
        state.shuffle shouldBe true
        state.repeatMode shouldBe S2RepeatMode.One

        playbackManager.playbackStateFlow.value = PlaybackState.Loading
        viewModel.uiState.value.buffering shouldBe true
        viewModel.uiState.value.playing shouldBe false
    }

    @Test
    fun `shuffle and repeat toggle the queue's modes`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))

        viewModel.toggleShuffle()
        viewModel.cycleRepeatMode()

        viewModel.uiState.value.shuffle shouldBe true
        viewModel.uiState.value.repeatMode shouldBe S2RepeatMode.All
    }

    @Test
    fun `transport actions reach playback`() = runTest {
        val viewModel = viewModel()
        viewModel.togglePlayback()
        viewModel.skipToNext()
        viewModel.skipToPrevious()
        viewModel.seekTo(42_000)

        playbackManager.calls shouldBe listOf("togglePlayback()", "skipToNext(true)", "skipToPrev()", "seekTo(42000)")
    }

    @Test
    fun `progress falls back to the song's saved position until playback reports one`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 200_000)))
        viewModel.progress.value shouldBe PlayerProgress(1, 200_000)

        playbackManager.progressFlow.value = PlaybackProgress(position = 50_000, duration = 200_000)
        viewModel.progress.value shouldBe PlayerProgress(50_000, 200_000)
        viewModel.progress.value.fraction shouldBe 0.25f
    }

    @Test
    fun `the total is the song's duration, as the queue rows show it, not the player's`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 60_000)))
        playbackManager.progressFlow.value = PlaybackProgress(position = 10_000, duration = 59_950)
        viewModel.progress.value shouldBe PlayerProgress(10_000, 60_000)
    }

    @Test
    fun `without a song duration the total is the player's`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 0)))
        playbackManager.progressFlow.value = PlaybackProgress(position = 10_000, duration = 59_950)
        viewModel.progress.value shouldBe PlayerProgress(10_000, 59_950)
    }

    @Test
    fun `the favourite follows the favourites playlist, and toggling adds then removes the song`() = runTest {
        val favourites = createPlaylist(id = 9, name = "Favorites")
        playlistRepository.favorites = favourites
        val viewModel = viewModel()
        val song = songs("One").single()
        queueManager.queueStateFlow.value = queueOf(listOf(song))
        viewModel.uiState.value.favourite shouldBe false

        viewModel.toggleFavourite()
        viewModel.uiState.value.favourite shouldBe true
        playlistRepository.getSongsForPlaylist(favourites).first().map { it.song } shouldBe listOf(song)

        viewModel.toggleFavourite()
        viewModel.uiState.value.favourite shouldBe false
    }

    @Test
    fun `without a favourites playlist nothing is a favourite`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        viewModel.uiState.value.favourite shouldBe false
    }

    @Test
    fun `the seed is extracted once per album`() = runTest {
        val viewModel = viewModel()
        val firstAlbum = songs("One", "Two", album = "First")
        queueManager.queueStateFlow.value = queueOf(firstAlbum + createSong(id = 7, name = "Three", album = "Second"))
        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(Color.Red)

        queueManager.queueStateFlow.value = queueOf(firstAlbum, current = 1)
        seededSongs.map { it.name } shouldBe listOf("One")

        queueManager.queueStateFlow.value = queueOf(firstAlbum + createSong(id = 7, name = "Three", album = "Second"), current = 2)
        seededSongs.map { it.name } shouldBe listOf("One", "Three")
    }

    @Test
    fun `queue actions resolve rows by uid`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One", "Two", "Three", "Four"), current = 1)

        viewModel.skipToQueueItem(102)
        viewModel.moveQueueItem(103, afterUid = null)
        viewModel.moveQueueItem(102, afterUid = 101)
        viewModel.removeQueueItem(100)
        viewModel.playNext(103)
        viewModel.playNext(100)
        viewModel.playNext(101)

        playbackManager.calls shouldBe listOf(
            "skipTo(2)",
            "moveQueueItem(3, 0)",
            "removeQueueItem(100)",
            // After the current song: a later one lands just after it, an earlier one takes its slot.
            "moveQueueItem(3, 2)",
            "moveQueueItem(0, 1)",
        )
    }

    @Test
    fun `a move resolves against the live queue, so rows added or removed mid-drag don't shift it`() = runTest {
        val viewModel = viewModel()
        val songs = songs("One", "Two", "Three", "Four")
        // The drag began on One, Two, Three, Four (uids 100-103): One was dragged to follow Three.
        queueManager.queueStateFlow.value = queueOf(songs)
        // Before the drop, auto-advance and a removal changed the queue: Two is gone, a new row leads.
        val live = listOf(QueueItem(uid = 200, song = createSong(id = 9, name = "New"), isCurrent = true)) +
            queueOf(songs).items.filter { it.uid != 101L }.map { it.clone(isCurrent = false) }
        queueManager.queueStateFlow.value = QueueState.Empty.copy(items = live, currentItem = live.first(), currentPosition = 0, isRestored = true)

        viewModel.moveQueueItem(100, afterUid = 102)

        // Live order New, One, Three, Four: One moves from 1 to just after Three, 2.
        playbackManager.calls shouldBe listOf("moveQueueItem(1, 2)")
    }

    @Test
    fun `a move is dropped when the dragged row or its new neighbour has left the queue`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One", "Two", "Three"))

        viewModel.moveQueueItem(999, afterUid = 102)
        viewModel.moveQueueItem(100, afterUid = 999)

        playbackManager.calls shouldBe emptyList<String>()
    }

    @Test
    fun `queueMove puts the row just after its neighbour`() {
        val uids = listOf(1L, 2L, 3L, 4L)
        queueMove(uids, 1, afterUid = 3) shouldBe (0 to 2)
        queueMove(uids, 4, afterUid = 1) shouldBe (3 to 1)
        queueMove(uids, 3, afterUid = null) shouldBe (2 to 0)
        queueMove(uids, 2, afterUid = 1) shouldBe null
        queueMove(uids, 1, afterUid = null) shouldBe null
    }

    @Test
    fun `removing a row reports it, and undo puts its song back where it was`() = runTest {
        val viewModel = viewModel()
        val (one, two, three, four) = songs("One", "Two", "Three", "Four")
        queueManager.queueStateFlow.value = queueOf(listOf(one, two, three, four))
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        viewModel.removeQueueItem(101)
        events shouldBe listOf(PlayerUiEvent.QueueItemRemoved)
        queueManager.queueStateFlow.value = queueOf(listOf(one, three, four))
        playbackManager.onAddToQueue = { songs -> queueManager.queueStateFlow.value = queueOf(listOf(one, three, four) + songs) }

        viewModel.undoRemoveQueueItem()
        playbackManager.addedToQueue shouldBe listOf(two)
        // Re-added at the end (3), then back to where it was (1).
        playbackManager.calls shouldBe listOf("removeQueueItem(101)", "moveQueueItem(3, 1)")

        // Undo is spent once used.
        viewModel.undoRemoveQueueItem()
        playbackManager.addedToQueue shouldBe listOf(two)
    }

    @Test
    fun `undoing the removal of the last row leaves it at the end`() = runTest {
        val viewModel = viewModel()
        val (one, two) = songs("One", "Two")
        queueManager.queueStateFlow.value = queueOf(listOf(one, two))

        viewModel.removeQueueItem(101)
        queueManager.queueStateFlow.value = queueOf(listOf(one))
        playbackManager.onAddToQueue = { songs -> queueManager.queueStateFlow.value = queueOf(listOf(one) + songs) }
        viewModel.undoRemoveQueueItem()

        playbackManager.addedToQueue shouldBe listOf(two)
        playbackManager.calls shouldBe listOf("removeQueueItem(101)")
    }

    @Test
    fun `the song menus offer the shared actions that suit a song already in the queue`() = runTest {
        val viewModel = viewModel()

        val offered = viewModel.songActions(createSong(id = 1)).first()

        offered.none { it in setOf(MediaActionType.Play, MediaActionType.Shuffle, MediaActionType.PlayNext, MediaActionType.AddToQueue, MediaActionType.Delete) } shouldBe true
        offered.take(3) shouldBe listOf(MediaActionType.AddToPlaylist, MediaActionType.GoToAlbum, MediaActionType.GoToArtist)
        offered.last() shouldBe MediaActionType.Exclude
    }

    @Test
    fun `a song action reports the handler's result`() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        // The fake library has no albums, so Go to album finds nothing.
        viewModel.onMediaAction(MediaAction.GoToAlbum(MediaSelection.Songs(createSong(id = 1))))

        events shouldBe listOf(PlayerUiEvent.MediaActionDone(MediaActionResult.Message(MediaActionMessage.NotFound)))
    }

    @Test
    fun `clearing the queue reports it, and undo puts it back where it was`() = runTest {
        val viewModel = viewModel()
        val songs = songs("One", "Two", "Three")
        queueManager.queueStateFlow.value = queueOf(songs, current = 1)
        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        playbackManager.savedProgress = 30_000
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        viewModel.clearQueue()
        events shouldBe listOf(PlayerUiEvent.QueueCleared(3))
        playbackManager.calls shouldBe listOf("clearQueue()")

        viewModel.undoClearQueue()
        queueManager.lastSetQueue shouldBe songs
        queueManager.lastSetShuffleQueue shouldBe null
        queueManager.lastSetQueuePosition shouldBe 1
        playbackManager.loadedPositions shouldBe listOf(30_000)
        playbackManager.calls shouldBe listOf("clearQueue()", "play()")

        // Undo is spent once used.
        viewModel.undoClearQueue()
        playbackManager.loadedPositions shouldBe listOf(30_000)
    }

    @Test
    fun `clearing an empty queue does nothing`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)
        viewModel.clearQueue()
        playbackManager.calls shouldBe emptyList<String>()
    }

    @Test
    fun `undo leaves a paused queue paused`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        viewModel.clearQueue()
        viewModel.undoClearQueue()
        playbackManager.calls shouldBe listOf("clearQueue()")
    }

    @Test
    fun `the speed follows the player and the ReplayGain mode follows its setting`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.value.playbackSpeed shouldBe 1f

        viewModel.setPlaybackSpeed(1.5f)
        viewModel.uiState.value.playbackSpeed shouldBe 1.5f
        playbackManager.getPlaybackSpeed() shouldBe 1.5f

        viewModel.setReplayGainMode(ReplayGainMode.Album)
        viewModel.uiState.value.replayGainMode shouldBe ReplayGainMode.Album
        replayGainPreference.mode.value shouldBe ReplayGainMode.Album
    }

    @Test
    fun `the sleep timer shows as running until it goes off, and remembers play to end`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))

        viewModel.startSleepTimer(durationMs = 5_000, playToEnd = true)
        viewModel.uiState.value.sleepTimerActive shouldBe true
        viewModel.uiState.value.sleepTimerPlayToEnd shouldBe true
        sleepTimerPreference.playToEnd shouldBe true
        viewModel.sleepTimerRemaining().first() shouldBe 5_000

        viewModel.stopSleepTimer()
        viewModel.uiState.value.sleepTimerActive shouldBe false
        viewModel.sleepTimerRemaining().first() shouldBe null
    }

    @Test
    fun `a sleep timer going off clears the running state`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))

        viewModel.startSleepTimer(durationMs = 3_000, playToEnd = false)
        advanceTimeBy(4_500)
        viewModel.uiState.value.sleepTimerActive shouldBe false
    }

    @Test
    fun `a panel toggles open and shut, and showing another replaces it`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        viewModel.uiState.value.panel shouldBe null

        viewModel.togglePanel(NowPlayingPanel.SleepTimer)
        viewModel.uiState.value.panel shouldBe NowPlayingPanel.SleepTimer
        viewModel.togglePanel(NowPlayingPanel.PlaybackSound)
        viewModel.uiState.value.panel shouldBe NowPlayingPanel.PlaybackSound
        viewModel.togglePanel(NowPlayingPanel.PlaybackSound)
        viewModel.uiState.value.panel shouldBe null
        viewModel.showPanel(NowPlayingPanel.Queue)
        viewModel.uiState.value.panel shouldBe NowPlayingPanel.Queue
        viewModel.showPanel(null)
        viewModel.uiState.value.panel shouldBe null
    }

    @Test
    fun `the open panel is saved, so it survives process death`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).showPanel(NowPlayingPanel.Queue)

        val restored = viewModel(SavedStateHandle(mapOf(PlayerViewModel.PANEL_KEY to handle.get<NowPlayingPanel>(PlayerViewModel.PANEL_KEY))))
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        restored.uiState.value.panel shouldBe NowPlayingPanel.Queue
    }

    @Test
    fun `an emptied queue shows no panel`() = runTest {
        val viewModel = viewModel()
        queueManager.queueStateFlow.value = queueOf(songs("One"))
        viewModel.showPanel(NowPlayingPanel.SleepTimer)

        queueManager.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)
        viewModel.uiState.value.panel shouldBe null
    }
}
