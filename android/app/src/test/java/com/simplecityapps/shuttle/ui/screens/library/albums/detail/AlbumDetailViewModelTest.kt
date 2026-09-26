package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import androidx.compose.ui.graphics.Color
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.designsystem.theme.ArtworkSeed
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.shuttle.ui.actions.ObserveCurrentSong
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import com.simplecityapps.shuttle.ui.theme.ObserveArtworkSeed
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Direct ViewModel state tests, replacing the deleted AlbumDetail integration tests (#478). */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AlbumDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seededAlbums = mutableListOf<String?>()
    private val seedSource = ArtworkSeedSource { song ->
        seededAlbums += song.album
        ArtworkSeed.Available(Color.Red)
    }
    private val settingsStore = SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() })

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

        viewModel.uiState.value.seed shouldBe ArtworkSeed.Available(Color.Red)
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

    private fun createViewModel(): AlbumDetailViewModel {
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            FakeQueueOperations(),
            playbackOperations = FakePlaybackOperations(),
            albumRepository = fakeAlbumRepository,
        )
        return AlbumDetailViewModel(
            groupKey = testAlbum.groupKey,
            observeSongs = testMediaActions.observeSongs,
            observeAlbums = testMediaActions.observeAlbums,
            observeCurrentSong = ObserveCurrentSong(fakeQueueOperations),
            observeArtworkSeed = ObserveArtworkSeed(seedSource, ObserveSetting(settingsStore)),
        )
    }
}
