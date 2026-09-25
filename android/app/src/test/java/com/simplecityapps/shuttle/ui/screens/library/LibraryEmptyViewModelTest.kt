package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccess
import com.simplecityapps.shuttle.ui.screens.sources.MusicAccessCoordinator
import com.simplecityapps.shuttle.ui.screens.sources.SourcesSettings
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [LibraryEmptyViewModel] itself just forwards to [MusicAccessCoordinator] (#427); permission and scan-once
 * semantics are covered by `MusicAccessCoordinatorTest`. This checks the forwarding, and that two instances — one
 * for Home, one for Library — share that coordinator's state instead of tracking it separately.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryEmptyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository()
    private val importState = FakeSongImportStateProvider()
    private val mediaSources = FakeMediaSources()
    private val settings = SourcesSettings(SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }))

    private fun TestScope.musicAccess() = MusicAccessCoordinator(songRepository, importState, mediaSources, settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    private fun TestScope.viewModel(musicAccess: MusicAccessCoordinator) = LibraryEmptyViewModel(musicAccess).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `uiState mirrors the shared coordinator's availability`() = runTest {
        songRepository.setSongs(emptyList())
        val musicAccess = musicAccess()
        val viewModel = viewModel(musicAccess)

        viewModel.onAccessChecked(granted = false, showRationale = false)

        viewModel.uiState.value shouldBe musicAccess.availability.value
        viewModel.uiState.value shouldBe LibraryAvailability.Empty(MusicAccess.NotRequested)
    }

    @Test
    fun `onAccessResult and onScan forward to the coordinator`() = runTest {
        songRepository.setSongs(emptyList())
        val musicAccess = musicAccess()
        val viewModel = viewModel(musicAccess)

        viewModel.onAccessResult(granted = true, showRationale = false)
        viewModel.uiState.value shouldBe LibraryAvailability.Empty(MusicAccess.Granted)
        mediaSources.scans shouldBe 1

        viewModel.onScan()
        mediaSources.scans shouldBe 2
    }

    @Test
    fun `a second screen's ViewModel sees the access the first screen already checked`() = runTest {
        songRepository.setSongs(emptyList())
        val musicAccess = musicAccess()
        val home = viewModel(musicAccess)
        val library = viewModel(musicAccess)

        home.onAccessChecked(granted = true, showRationale = false) // Home resumes first, checks access

        library.uiState.value shouldBe LibraryAvailability.Empty(MusicAccess.Granted)
    }

    @Test
    fun `a second screen resuming after the first doesn't scan again`() = runTest {
        songRepository.setSongs(emptyList())
        val musicAccess = musicAccess()
        val home = viewModel(musicAccess)
        val library = viewModel(musicAccess)

        home.onAccessChecked(granted = true, showRationale = false) // Home resumes first
        library.onAccessChecked(granted = true, showRationale = false) // Library resumes next

        mediaSources.scans shouldBe 1
    }
}
