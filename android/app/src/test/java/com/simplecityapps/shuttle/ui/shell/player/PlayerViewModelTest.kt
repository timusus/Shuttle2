package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import com.simplecityapps.createPlaylist
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSharedPreferences
import com.simplecityapps.fakes.FakeSongDownloadRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.clone
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.sleeptimer.SleepTimer
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.AvailableMediaActions
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaActionMessage
import com.simplecityapps.shuttle.ui.actions.MediaActionResult
import com.simplecityapps.shuttle.ui.actions.MediaActionType
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.shuttle.ui.actions.ObserveFavouriteSongIds
import com.simplecityapps.shuttle.ui.actions.ObservePlaylists
import com.simplecityapps.shuttle.ui.actions.ToggleFavourite
import com.simplecityapps.shuttle.ui.screens.settings.FakeSettingsEffects
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val playbackOperations = FakePlaybackOperations()
    private val queueOperations = FakeQueueOperations()
    private var savedNowPlaying: NowPlayingSnapshot? = null
    private val playlistRepository = FakePlaylistRepository()
    private val preferences = FakeSharedPreferences()
    private val settingsStore = SettingsStore(preferences)
    private val preferenceManager = GeneralPreferenceManager(preferences)
    private val settingsEffects = FakeSettingsEffects()
    private val seededSongs = mutableListOf<Song>()
    private val seedSource = ArtworkSeedSource { song ->
        seededSongs += song
        ArtworkSeed.Available(Color.Red)
    }
    private val gatedSongs = MutableSharedFlow<Song>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): PlayerViewModel {
        val sleepTimer = SleepTimer(playbackOperations, backgroundScope, UnconfinedTestDispatcher(testScheduler)) { testScheduler.currentTime }
        val mediaActions = TestMediaActions(playlistRepository = playlistRepository, queueOperations = queueOperations, playbackOperations = playbackOperations)
        return PlayerViewModel(
            observeQueue = ObserveQueue(queueOperations),
            observePlayback = ObservePlayback(playbackOperations, queueOperations),
            observeProgress = ObserveProgress(playbackOperations),
            observeGatedServerSkip = ObserveGatedServerSkip { gatedSongs },
            controlPlayback = ControlPlayback(playbackOperations, queueOperations),
            editQueue = EditQueue(playbackOperations, queueOperations),
            observeFavouriteSongIds = ObserveFavouriteSongIds(playlistRepository),
            setFavourite = ToggleFavourite(playlistRepository),
            observePlaylists = ObservePlaylists(playlistRepository),
            controlSleepTimer = ControlSleepTimer(sleepTimer, preferenceManager),
            readSleepTimeRemaining = ReadSleepTimeRemaining(sleepTimer),
            readSleepTimerPlayToEnd = ReadSleepTimerPlayToEnd(preferenceManager),
            observeSetting = ObserveSetting(settingsStore),
            setReplayGainMode = SetReplayGainMode(SaveSetting(settingsStore), settingsEffects),
            observeArtworkSeed = ObserveArtworkSeed(seedSource, ObserveSetting(settingsStore)),
            castAvailability = { false },
            savedNowPlaying = { savedNowPlaying },
            clearQueue = ClearQueue(queueOperations, playbackOperations),
            restoreQueue = RestoreQueue(queueOperations, playbackOperations),
            availableMediaActions = AvailableMediaActions(mediaActions.resolveSongs, FakeSongDownloadRepository()),
            mediaActionHandler = mediaActions.handler,
            savedStateHandle = savedStateHandle,
        ).also { viewModel ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
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
    fun `RS-63 a cold start shows the saved song, where it was left, until the queue is restored`() = runTest {
        savedNowPlaying = NowPlayingSnapshot.of(createSong(id = 7, name = "Saved", album = "Album", duration = 180_000)).copy(positionMs = 42_000)
        val viewModel = viewModel()

        viewModel.uiState.value.player.hasQueue shouldBe true
        viewModel.uiState.value.player.current?.title shouldBe "Saved"
        viewModel.uiState.value.player.current?.album shouldBe "Album"
        viewModel.uiState.value.player.current?.durationMs shouldBe 180_000
        viewModel.uiState.value.progress shouldBe PlayerProgress(42_000, 180_000)

        // The restored queue takes over, even when its song isn't the saved one.
        queueOperations.queueStateFlow.value = queueOf(songs("Restored"))

        viewModel.uiState.value.player.current?.title shouldBe "Restored"
        viewModel.uiState.value.player.items.map { it.title } shouldBe listOf("Restored")
    }

    @Test
    fun `a restore that brings nothing back takes the saved song away`() = runTest {
        savedNowPlaying = NowPlayingSnapshot.of(createSong(id = 7, name = "Saved"))
        val viewModel = viewModel()

        queueOperations.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)

        viewModel.uiState.value.player.hasQueue shouldBe false
        viewModel.uiState.value.player.current shouldBe null
    }

    @Test
    fun `play on the saved song plays once the queue is restored`() = runTest {
        savedNowPlaying = NowPlayingSnapshot.of(createSong(id = 7, name = "Saved"))
        val viewModel = viewModel()

        viewModel.togglePlayback()
        playbackOperations.calls shouldBe emptyList()

        queueOperations.queueStateFlow.value = queueOf(songs("Saved"))

        playbackOperations.calls shouldBe listOf("play()")
    }

    @Test
    fun `play on the saved song does nothing when the restore brings nothing back`() = runTest {
        savedNowPlaying = NowPlayingSnapshot.of(createSong(id = 7, name = "Saved"))
        val viewModel = viewModel()

        viewModel.togglePlayback()
        queueOperations.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)

        playbackOperations.calls shouldBe emptyList()
        viewModel.uiState.value.player.hasQueue shouldBe false
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
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        queueOperations.shuffleModeFlow.value = ShuffleMode.On
        queueOperations.repeatModeFlow.value = RepeatMode.One

        val state = viewModel.uiState.value.player
        state.current?.title shouldBe "One"
        state.playing shouldBe true
        state.shuffle shouldBe true
        state.repeatMode shouldBe S2RepeatMode.One

        playbackOperations.playbackStateFlow.value = PlaybackState.Loading
        viewModel.uiState.value.player.buffering shouldBe true
        viewModel.uiState.value.player.playing shouldBe false
    }

    @Test
    fun `shuffle and repeat toggle the queue's modes`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))

        viewModel.toggleShuffle()
        viewModel.cycleRepeatMode()

        viewModel.uiState.value.player.shuffle shouldBe true
        viewModel.uiState.value.player.repeatMode shouldBe S2RepeatMode.All
    }

    @Test
    fun `transport actions reach playback`() = runTest {
        val viewModel = viewModel()
        viewModel.togglePlayback()
        viewModel.skipToNext()
        viewModel.skipToPrevious()
        viewModel.seekTo(42_000)

        playbackOperations.calls shouldBe listOf("togglePlayback()", "skipToNext(true)", "skipToPrev()", "seekTo(42000)")
    }

    @Test
    fun `progress falls back to the song's saved position until playback reports one`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 200_000)))
        viewModel.uiState.value.progress shouldBe PlayerProgress(1, 200_000)

        playbackOperations.progressFlow.value = PlaybackProgress(position = 50_000, duration = 200_000)
        viewModel.uiState.value.progress shouldBe PlayerProgress(50_000, 200_000)
        viewModel.uiState.value.progress.fraction shouldBe 0.25f
    }

    @Test
    fun `the total is the song's duration, as the queue rows show it, not the player's`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 60_000)))
        playbackOperations.progressFlow.value = PlaybackProgress(position = 10_000, duration = 59_950)
        viewModel.uiState.value.progress shouldBe PlayerProgress(10_000, 60_000)
    }

    @Test
    fun `without a song duration the total is the player's`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(listOf(createSong(name = "One", duration = 0)))
        playbackOperations.progressFlow.value = PlaybackProgress(position = 10_000, duration = 59_950)
        viewModel.uiState.value.progress shouldBe PlayerProgress(10_000, 59_950)
    }

    @Test
    fun `the favourite follows the favourites playlist, and toggling adds then removes the song`() = runTest {
        val favourites = createPlaylist(id = 9, name = "Favorites")
        playlistRepository.favorites = favourites
        val viewModel = viewModel()
        val song = songs("One").single()
        queueOperations.queueStateFlow.value = queueOf(listOf(song))
        viewModel.uiState.value.player.favourite shouldBe false

        viewModel.toggleFavourite()
        viewModel.uiState.value.player.favourite shouldBe true
        playlistRepository.getSongsForPlaylist(favourites).first().map { it.song } shouldBe listOf(song)

        viewModel.toggleFavourite()
        viewModel.uiState.value.player.favourite shouldBe false
    }

    @Test
    fun `without a favourites playlist nothing is a favourite`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        viewModel.uiState.value.player.favourite shouldBe false
    }

    @Test
    fun `the seed follows the playing song's own artwork, and a queue change around it doesn't extract again`() = runTest {
        val viewModel = viewModel()
        val album = songs("One", "Two", album = "First")
        queueOperations.queueStateFlow.value = queueOf(album)
        viewModel.uiState.value.player.seed shouldBe ArtworkSeed.Available(Color.Red)

        queueOperations.queueStateFlow.value = queueOf(album + createSong(id = 7, name = "Three", album = "Second"))
        seededSongs.map { it.name } shouldBe listOf("One")

        // A track on the same album can carry its own artwork, so it seeds on its own
        queueOperations.queueStateFlow.value = queueOf(album, current = 1)
        seededSongs.map { it.name } shouldBe listOf("One", "Two")
    }

    @Test
    fun `turning off Colour from artwork drops the seed, and no extraction runs`() = runTest {
        SaveSetting(settingsStore)(AppearanceSettings.ColourFromArtwork, false)
        val viewModel = viewModel()

        queueOperations.queueStateFlow.value = queueOf(songs("One"))

        viewModel.uiState.value.player.seed shouldBe ArtworkSeed.None
        seededSongs shouldBe emptyList()
    }

    @Test
    fun `queue actions resolve rows by uid`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One", "Two", "Three", "Four"), current = 1)

        viewModel.skipToQueueItem(102)
        viewModel.moveQueueItem(103, afterUid = null)
        viewModel.moveQueueItem(102, afterUid = 101)
        viewModel.removeQueueItem(100)
        viewModel.playNext(103)
        viewModel.playNext(100)
        viewModel.playNext(101)

        playbackOperations.calls shouldBe listOf(
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
        queueOperations.queueStateFlow.value = queueOf(songs)
        // Before the drop, auto-advance and a removal changed the queue: Two is gone, a new row leads.
        val live = listOf(QueueItem(uid = 200, song = createSong(id = 9, name = "New"), isCurrent = true)) +
            queueOf(songs).items.filter { it.uid != 101L }.map { it.clone(isCurrent = false) }
        queueOperations.queueStateFlow.value = QueueState.Empty.copy(items = live, currentItem = live.first(), currentPosition = 0, isRestored = true)

        viewModel.moveQueueItem(100, afterUid = 102)

        // Live order New, One, Three, Four: One moves from 1 to just after Three, 2.
        playbackOperations.calls shouldBe listOf("moveQueueItem(1, 2)")
    }

    @Test
    fun `a move is dropped when the dragged row or its new neighbour has left the queue`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One", "Two", "Three"))

        viewModel.moveQueueItem(999, afterUid = 102)
        viewModel.moveQueueItem(100, afterUid = 999)

        playbackOperations.calls shouldBe emptyList<String>()
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
        queueOperations.queueStateFlow.value = queueOf(listOf(one, two, three, four))
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        viewModel.removeQueueItem(101)
        events shouldBe listOf(PlayerUiEvent.QueueItemRemoved)
        queueOperations.queueStateFlow.value = queueOf(listOf(one, three, four))
        playbackOperations.onAddToQueue = { songs -> queueOperations.queueStateFlow.value = queueOf(listOf(one, three, four) + songs) }

        viewModel.undoRemoveQueueItem()
        playbackOperations.addedToQueue shouldBe listOf(two)
        // Re-added at the end (3), then back to where it was (1).
        playbackOperations.calls shouldBe listOf("removeQueueItem(101)", "moveQueueItem(3, 1)")

        // Undo is spent once used.
        viewModel.undoRemoveQueueItem()
        playbackOperations.addedToQueue shouldBe listOf(two)
    }

    @Test
    fun `undoing the removal of the last row leaves it at the end`() = runTest {
        val viewModel = viewModel()
        val (one, two) = songs("One", "Two")
        queueOperations.queueStateFlow.value = queueOf(listOf(one, two))

        viewModel.removeQueueItem(101)
        queueOperations.queueStateFlow.value = queueOf(listOf(one))
        playbackOperations.onAddToQueue = { songs -> queueOperations.queueStateFlow.value = queueOf(listOf(one) + songs) }
        viewModel.undoRemoveQueueItem()

        playbackOperations.addedToQueue shouldBe listOf(two)
        playbackOperations.calls shouldBe listOf("removeQueueItem(101)")
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
        queueOperations.queueStateFlow.value = queueOf(songs, current = 1)
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        playbackOperations.savedProgress = 30_000
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        viewModel.clearQueue()
        events shouldBe listOf(PlayerUiEvent.QueueCleared(3))
        playbackOperations.calls shouldBe listOf("clearQueue()")

        viewModel.undoClearQueue()
        queueOperations.lastSetQueue shouldBe songs
        queueOperations.lastSetShuffleQueue shouldBe null
        queueOperations.lastSetQueuePosition shouldBe 1
        playbackOperations.loadedPositions shouldBe listOf(30_000)
        playbackOperations.calls shouldBe listOf("clearQueue()", "play()")

        // Undo is spent once used.
        viewModel.undoClearQueue()
        playbackOperations.loadedPositions shouldBe listOf(30_000)
    }

    @Test
    fun `clearing an empty queue does nothing`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)
        viewModel.clearQueue()
        playbackOperations.calls shouldBe emptyList<String>()
    }

    @Test
    fun `undo leaves a paused queue paused`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        viewModel.clearQueue()
        viewModel.undoClearQueue()
        playbackOperations.calls shouldBe listOf("clearQueue()")
        // A restored song that can't load stays where it was left, rather than the queue moving on (RS-56).
        playbackOperations.loadedSkipUnloadable shouldBe listOf(false)
    }

    @Test
    fun `the speed follows the player and the ReplayGain mode follows its setting`() = runTest {
        val viewModel = viewModel()
        viewModel.uiState.value.player.playbackSpeed shouldBe 1f

        viewModel.setPlaybackSpeed(1.5f)
        viewModel.uiState.value.player.playbackSpeed shouldBe 1.5f
        playbackOperations.getPlaybackSpeed() shouldBe 1.5f

        viewModel.setReplayGainMode(ReplayGainMode.Album)
        viewModel.uiState.value.player.replayGainMode shouldBe ReplayGainMode.Album
        ReadSetting(settingsStore)(PlaybackSettings.ReplayGain) shouldBe ReplayGainMode.Album
        settingsEffects.changes shouldBe listOf(PlaybackSettings.ReplayGain.key to ReplayGainMode.Album)
    }

    @Test
    fun `the sleep timer shows as running until it goes off, and remembers play to end`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))

        viewModel.startSleepTimer(durationMs = 5_000, playToEnd = true)
        viewModel.uiState.value.player.sleepTimerActive shouldBe true
        viewModel.uiState.value.player.sleepTimerPlayToEnd shouldBe true
        preferenceManager.sleepTimerPlayToEnd shouldBe true
        viewModel.sleepTimerRemaining().first() shouldBe 5_000

        viewModel.stopSleepTimer()
        viewModel.uiState.value.player.sleepTimerActive shouldBe false
        viewModel.sleepTimerRemaining().first() shouldBe null
    }

    @Test
    fun `a sleep timer going off clears the running state`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))

        viewModel.startSleepTimer(durationMs = 3_000, playToEnd = false)
        advanceTimeBy(4_500)
        viewModel.uiState.value.player.sleepTimerActive shouldBe false
    }

    @Test
    fun `a panel toggles open and shut, and showing another replaces it`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        viewModel.uiState.value.player.panel shouldBe null

        viewModel.togglePanel(NowPlayingPanel.SleepTimer)
        viewModel.uiState.value.player.panel shouldBe NowPlayingPanel.SleepTimer
        viewModel.togglePanel(NowPlayingPanel.PlaybackSound)
        viewModel.uiState.value.player.panel shouldBe NowPlayingPanel.PlaybackSound
        viewModel.togglePanel(NowPlayingPanel.PlaybackSound)
        viewModel.uiState.value.player.panel shouldBe null
        viewModel.showPanel(NowPlayingPanel.Queue)
        viewModel.uiState.value.player.panel shouldBe NowPlayingPanel.Queue
        viewModel.showPanel(null)
        viewModel.uiState.value.player.panel shouldBe null
    }

    @Test
    fun `the open panel is saved, so it survives process death`() = runTest {
        val handle = SavedStateHandle()
        viewModel(handle).showPanel(NowPlayingPanel.Queue)

        val restored = viewModel(SavedStateHandle(mapOf(PlayerViewModel.PANEL_KEY to handle.get<NowPlayingPanel>(PlayerViewModel.PANEL_KEY))))
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        restored.uiState.value.player.panel shouldBe NowPlayingPanel.Queue
    }

    @Test
    fun `a gated server song is reported as a skip event`() = runTest {
        val viewModel = viewModel()
        val events = mutableListOf<PlayerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }

        gatedSongs.emit(createSong(name = "Remote Song"))

        events shouldBe listOf(PlayerUiEvent.ServerSongSkipped("Remote Song"))
    }

    @Test
    fun `an emptied queue shows no panel`() = runTest {
        val viewModel = viewModel()
        queueOperations.queueStateFlow.value = queueOf(songs("One"))
        viewModel.showPanel(NowPlayingPanel.SleepTimer)

        queueOperations.queueStateFlow.value = QueueState.Empty.copy(isRestored = true)
        viewModel.uiState.value.player.panel shouldBe null
    }
}
