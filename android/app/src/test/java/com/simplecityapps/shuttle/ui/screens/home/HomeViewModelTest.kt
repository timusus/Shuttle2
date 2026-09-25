package com.simplecityapps.shuttle.ui.screens.home

import android.content.Context
import com.simplecityapps.createAlbum
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeAlbumArtistRepository
import com.simplecityapps.fakes.FakeAlbumRepository
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.MediaAction
import com.simplecityapps.shuttle.ui.actions.MediaSelection
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var preferenceManager: GeneralPreferenceManager
    private val songs = FakeSongRepository()
    private val albums = FakeAlbumRepository()

    private val airbag = createSong(id = 1, name = "Airbag", albumArtist = "Radiohead", album = "OK Computer")

    @Before
    fun setUp() {
        preferenceManager = GeneralPreferenceManager(context.getSharedPreferences("home-test", Context.MODE_PRIVATE).apply { edit().clear().commit() })
        preferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }

    private fun TestScope.viewModel(): HomeViewModel {
        val sections = HomeSections(albums, FakeAlbumArtistRepository(), songs, seed = 1, dispatcher = mainDispatcherRule.testDispatcher)
        return HomeViewModel(sections, preferenceManager).also { viewModel ->
            backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
        }
    }

    @Test
    fun `an empty library is the empty state`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().uiState.value shouldBe HomeUiState.Empty
    }

    @Test
    fun `a library shows its shelves`() = runTest(mainDispatcherRule.testDispatcher) {
        val often = createAlbum("OK Computer", "Radiohead", playCount = 5)
        songs.setSongs(listOf(airbag))
        albums.setAlbums(listOf(often))

        val content = viewModel().uiState.value.shouldBeInstanceOf<HomeUiState.Content>()
        content.mostPlayed shouldBe listOf(often)
        content.showWhatsNew shouldBe false
    }

    @Test
    fun `shuffle all shuffles every song`() = runTest(mainDispatcherRule.testDispatcher) {
        songs.setSongs(listOf(airbag))

        viewModel().shuffleAll() shouldBe MediaAction.Shuffle(MediaSelection.Songs(listOf(airbag)))
    }

    @Test
    fun `shuffle all does nothing on an empty library`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().shuffleAll().shouldBeNull()
    }

    @Test
    fun `unseen release notes show the whats new card until handled`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.lastViewedChangelogVersion = "2020.01.01"
        songs.setSongs(listOf(airbag))
        val viewModel = viewModel()
        (viewModel.uiState.value as HomeUiState.Content).showWhatsNew shouldBe true

        viewModel.onWhatsNewHandled()
        runCurrent()

        (viewModel.uiState.value as HomeUiState.Content).showWhatsNew shouldBe false
        preferenceManager.lastViewedChangelogVersion shouldBe BuildConfig.VERSION_NAME
    }

    @Test
    fun `the whats new card stays hidden when changelogs are turned off`() = runTest(mainDispatcherRule.testDispatcher) {
        preferenceManager.lastViewedChangelogVersion = "2020.01.01"
        preferenceManager.showChangelogOnLaunch = false
        songs.setSongs(listOf(airbag))

        (viewModel().uiState.value as HomeUiState.Content).showWhatsNew shouldBe false
    }
}
