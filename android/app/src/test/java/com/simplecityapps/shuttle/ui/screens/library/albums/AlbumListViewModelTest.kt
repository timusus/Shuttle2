package com.simplecityapps.shuttle.ui.screens.library.albums

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumListPreferences
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeGenreRepository
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakePlaylistRepository
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.FakeSortPreferences
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.fakes.fakeLibraryViewPreferences
import com.simplecityapps.fakes.importComplete
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.ui.actions.ShuffleAlbums
import com.simplecityapps.shuttle.ui.screens.library.ReadLibraryViewSetting
import com.simplecityapps.shuttle.ui.screens.library.SaveLibraryViewSetting
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * Focused ViewModel unit tests for behaviour that can't be observed through the UI, notably
 * [AlbumListViewModel.onShuffle]'s side effects. State derivation and selection are tested via
 * [AlbumListIntegrationTest] (real ViewModel + real Composable + fakes).
 */
@ExperimentalCoroutinesApi
class AlbumListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeAlbumRepository = FakeAlbumRepository()
    private val fakeSongRepository = FakeSongRepository()
    private val fakePlaylistRepository = FakePlaylistRepository()
    private val fakeImportState = FakeSongImportStateProvider()
    private val fakeSortPreferences = FakeSortPreferences()
    private val fakeViewModePreferences = FakeAlbumListPreferences()
    private val fakeQueueOperations = FakeQueueOperations()

    @Test
    fun `onShuffle queues each album's songs together, in track order`() = runTest {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Side A Track 2", album = "Side A", track = 2),
                createSong(id = 2, name = "Side B Track 1", album = "Side B", track = 1),
                createSong(id = 3, name = "Side A Track 1", album = "Side A", track = 1),
                createSong(id = 4, name = "Side B Track 2", album = "Side B", track = 2),
            )
        )
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        val queuedNames = fakeQueueOperations.lastSetQueue.orEmpty().map { it.name }
        val possibleOrders = listOf(
            listOf("Side A Track 1", "Side A Track 2", "Side B Track 1", "Side B Track 2"),
            listOf("Side B Track 1", "Side B Track 2", "Side A Track 1", "Side A Track 2"),
        )
        (queuedNames in possibleOrders) shouldBe true
    }

    @Test
    fun `onShuffle keeps same-titled albums by different artists as separate units`() = runTest {
        fakeSongRepository.setSongs(
            listOf(
                createSong(id = 1, name = "Artist A Track 2", albumArtist = "Artist A", album = "Greatest Hits", track = 2),
                createSong(id = 2, name = "Artist B Track 1", albumArtist = "Artist B", album = "Greatest Hits", track = 1),
                createSong(id = 3, name = "Artist A Track 1", albumArtist = "Artist A", album = "Greatest Hits", track = 1),
                createSong(id = 4, name = "Artist B Track 2", albumArtist = "Artist B", album = "Greatest Hits", track = 2),
            )
        )
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        val queuedNames = fakeQueueOperations.lastSetQueue.orEmpty().map { it.name }
        val possibleOrders = listOf(
            listOf("Artist A Track 1", "Artist A Track 2", "Artist B Track 1", "Artist B Track 2"),
            listOf("Artist B Track 1", "Artist B Track 2", "Artist A Track 1", "Artist A Track 2"),
        )
        (queuedNames in possibleOrders) shouldBe true
    }

    @Test
    fun `onShuffle does not enable shuffle mode`() = runTest {
        fakeSongRepository.setSongs(listOf(createSong(id = 1, name = "Solo", album = "Only Album")))
        fakeImportState.setState(importComplete())
        val viewModel = createViewModel()

        viewModel.onShuffle()
        advanceUntilIdle()

        fakeQueueOperations.shuffleModeFlow.value shouldBe ShuffleMode.Off
    }

    private fun createViewModel(random: Random = Random.Default): AlbumListViewModel {
        val fakePlaybackOperations = FakePlaybackOperations()
        val preferences = fakeLibraryViewPreferences(sort = fakeSortPreferences, albumList = fakeViewModePreferences)
        val testMediaActions = TestMediaActions(
            fakeSongRepository,
            FakeGenreRepository(),
            fakePlaylistRepository,
            fakeQueueOperations,
            playbackOperations = fakePlaybackOperations,
            albumRepository = fakeAlbumRepository,
        )
        return AlbumListViewModel(
            observeAlbums = testMediaActions.observeAlbums,
            observeSongs = testMediaActions.observeSongs,
            shuffleAlbums = ShuffleAlbums(fakeQueueOperations, fakePlaybackOperations),
            readSetting = ReadLibraryViewSetting(preferences),
            saveSetting = SaveLibraryViewSetting(preferences),
            mediaImportObserver = fakeImportState,
            random = random,
        )
    }
}
