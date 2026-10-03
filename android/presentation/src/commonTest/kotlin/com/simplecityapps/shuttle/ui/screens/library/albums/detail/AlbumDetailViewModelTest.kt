package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.mediaprovider.repository.albums.AlbumQuery
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.AlbumIdentityRule
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** The seed the fake artwork source extracts, as sRGB ARGB. */
private const val RED = 0xFFFF0000.toInt()

/** Direct ViewModel state tests, replacing the deleted AlbumDetail integration tests (#478). */
@ExperimentalCoroutinesApi
class AlbumDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val seededAlbums = mutableListOf<String?>()
    private val seedSource = object : ArtworkSeedSource {
        override suspend fun seedFor(song: Song): ArtworkSeed {
            seededAlbums += song.album
            return ArtworkSeed.Available(RED)
        }

        override suspend fun seedFor(artist: AlbumArtist): ArtworkSeed = error("An album page seeds from its songs")
    }
    private val settingsStore = SettingsStore(InMemoryKeyValueStore())

    private val fakeSongRepository = FakeSongRepository()
    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeQueueOperations = FakeQueueOperations()

    private val testAlbum = createAlbum(
        name = "Cassette Summer",
        albumArtist = "The Tin Orchards",
        year = 1969,
        songCount = 17,
        duration = 2820000,
    )

    @Test
    fun `shows loading when repository has not emitted`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumDetailUiState.LoadingState.Loading
    }

    @Test
    fun `shows empty when no songs`() = runTest {
        fakeSongRepository.setSongs(emptyList())
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumDetailUiState.LoadingState.Empty
    }

    @Test
    fun `lists songs from the repository in order`() = runTest {
        val songs = listOf(
            createSong(id = 1, name = "Rewind Button"),
            createSong(id = 2, name = "Something"),
        )
        fakeSongRepository.setSongs(songs)
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.songs shouldBe songs
    }

    @Test
    fun `current song follows the queue's current item`() = runTest {
        val song = createSong(id = 1, name = "Rewind Button")
        fakeSongRepository.setSongs(listOf(song, createSong(id = 2, name = "Something")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.currentSong shouldBe null

        val queueItem = song.toQueueItem(isCurrent = true)
        fakeQueueOperations.queueStateFlow.value = QueueState(items = listOf(queueItem), currentItem = queueItem, currentPosition = 0)
        advanceUntilIdle()

        viewModel.uiState.value.currentSong shouldBe song
    }

    @Test
    fun `the album artwork tints the screen while Colour from artwork is on`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1, album = "Cassette Summer", albumArtist = "The Tin Orchards")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(RED)
        seededAlbums shouldBe listOf("Cassette Summer")
    }

    @Test
    fun `no tint while Colour from artwork is off`() = runTest {
        SaveSetting(settingsStore)(AppearanceSettings.ColourFromArtwork, false)
        fakeSongRepository.setSongs(listOf(createSong(id = 1, album = "Cassette Summer", albumArtist = "The Tin Orchards")))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.seed shouldBe ArtworkSeed.None
        seededAlbums shouldBe emptyList()
    }

    @Test
    fun `more by the artist excludes this album and lists the rest newest first`() = runTest {
        val older = createAlbum(name = "Tape Hiss", albumArtist = "The Tin Orchards", year = 1965)
        val newer = createAlbum(name = "Porch Light", albumArtist = "The Tin Orchards", year = 1975)
        val someoneElses = createAlbum(name = "Other", albumArtist = "Someone Else", year = 1980)
        fakeSongRepository.setSongs(listOf(createSong(id = 1)))
        fakeAlbumRepository.applyQueryPredicates = true
        fakeAlbumRepository.setAlbums(listOf(older, testAlbum, newer, someoneElses))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.moreByArtist shouldBe listOf(newer, older)
    }

    @Test
    fun `more by the artist is empty when the album is their only one`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1)))
        fakeAlbumRepository.applyQueryPredicates = true
        fakeAlbumRepository.setAlbums(listOf(testAlbum, createAlbum(name = "Other", albumArtist = "Someone Else")))
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.moreByArtist shouldBe emptyList()
    }

    @Test
    fun `more by the artist is empty when the album has no album artist`() = runTest {
        val unknownArtist = createAlbum(name = "Orphan", albumArtist = null, groupKey = AlbumGroupKey("Orphan", null))
        fakeSongRepository.setSongs(listOf(createSong(id = 1)))
        fakeAlbumRepository.applyQueryPredicates = true
        fakeAlbumRepository.setAlbums(listOf(unknownArtist, createAlbum(name = "Another Orphan", albumArtist = null, groupKey = AlbumGroupKey("Another Orphan", null))))
        val viewModel = createViewModel(unknownArtist.groupKey)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.moreByArtist shouldBe emptyList()
    }

    @Test
    fun `the album is ready before more by has emitted`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1)))
        fakeAlbumRepository.setAlbums(listOf(testAlbum))
        fakeAlbumRepository.neverEmits = { it is AlbumQuery.ArtistGroupKey }
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.loadingState shouldBe AlbumDetailUiState.LoadingState.Ready
        viewModel.uiState.value.moreByArtist shouldBe emptyList()
    }

    @Test
    fun `more by is empty for a compilation`() = runTest {
        val variousArtists = AlbumIdentityRule.VARIOUS_ARTISTS
        val compilation = createAlbum(name = "Now 1", albumArtist = variousArtists, groupKey = AlbumGroupKey("Now 1", AlbumArtistGroupKey(AlbumIdentityRule.artistKey(variousArtists))))
        val another = createAlbum(name = "Now 2", albumArtist = variousArtists, groupKey = AlbumGroupKey("Now 2", AlbumArtistGroupKey(AlbumIdentityRule.artistKey(variousArtists))))
        fakeSongRepository.setSongs(listOf(createSong(id = 1)))
        fakeAlbumRepository.applyQueryPredicates = true
        fakeAlbumRepository.setAlbums(listOf(compilation, another))
        val viewModel = createViewModel(compilation.groupKey)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.uiState.value.moreByArtist shouldBe emptyList()
    }

    private fun createViewModel(groupKey: AlbumGroupKey? = testAlbum.groupKey): AlbumDetailViewModel {
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueOperations(),
            playbackOperations = FakePlaybackOperations(),
            albumRepository = fakeAlbumRepository,
        )
        return AlbumDetailViewModel(
            groupKey = groupKey,
            observeSongs = testMediaActions.observeSongs,
            observeAlbums = testMediaActions.observeAlbums,
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
            observeArtworkSeed = ObserveArtworkSeed(seedSource, ObserveSetting(settingsStore)),
        )
    }
}
