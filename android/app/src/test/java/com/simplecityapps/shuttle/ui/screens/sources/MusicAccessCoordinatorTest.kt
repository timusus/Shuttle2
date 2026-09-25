package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.defaultSharedPreferences
import com.simplecityapps.shuttle.ui.screens.library.LibraryAvailability
import com.simplecityapps.shuttle.ui.screens.library.ScanProgress
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MusicAccessCoordinatorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songRepository = FakeSongRepository()
    private val importState = FakeSongImportStateProvider()
    private val mediaSources = FakeMediaSources()
    private val settings = SourcesSettings(SettingsStore(RuntimeEnvironment.getApplication().defaultSharedPreferences().apply { edit().clear().commit() }))

    private fun TestScope.coordinator() = MusicAccessCoordinator(songRepository, importState, mediaSources, settings, CoroutineScope(UnconfinedTestDispatcher(testScheduler))).also { coordinator ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { coordinator.availability.collect {} }
    }

    @Test
    fun `a library with songs has music`() = runTest {
        songRepository.setSongs(listOf(createSong(id = 1)))

        coordinator().availability.value shouldBe LibraryAvailability.HasMusic
    }

    @Test
    fun `first run offers access before it's ever been asked for`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()

        coordinator.onChecked(granted = false, showRationale = false)

        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.NotRequested)
    }

    @Test
    fun `a grant from the prompt enables the S2 scanner and scans`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()
        coordinator.onChecked(granted = false, showRationale = false)

        coordinator.onResult(granted = true, showRationale = false)

        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.Granted)
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
        settings.musicPermissionRequested.value shouldBe true
    }

    @Test
    fun `a refusal with a rationale is denied, and without one is permanent`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()

        coordinator.onResult(granted = false, showRationale = true)
        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.Denied)

        coordinator.onResult(granted = false, showRationale = false)
        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.PermanentlyDenied)
        mediaSources.scans shouldBe 0
    }

    @Test
    fun `a grant from the system settings starts the scan on resume`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()
        coordinator.onResult(granted = false, showRationale = false)

        coordinator.onChecked(granted = true, showRationale = false)

        mediaSources.scans shouldBe 1
    }

    @Test
    fun `a grant held when the app first opens scans, since nothing has been scanned yet`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()

        coordinator.onChecked(granted = true, showRationale = false)
        coordinator.onChecked(granted = true, showRationale = false)

        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.Granted)
        mediaSources.scans shouldBe 1
    }

    @Test
    fun `a grant held when the app first opens doesn't rescan a library that's already scanned`() = runTest {
        songRepository.setSongs(emptyList())
        mediaSources.hasScanned = true
        val coordinator = coordinator()

        coordinator.onChecked(granted = true, showRationale = false)

        mediaSources.scans shouldBe 0
    }

    @Test
    fun `resuming with the grant it already had doesn't scan again`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()
        coordinator.onResult(granted = true, showRationale = false)

        coordinator.onChecked(granted = true, showRationale = false)

        mediaSources.scans shouldBe 1
    }

    @Test
    fun `a running import shows its progress`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()
        coordinator.onChecked(granted = true, showRationale = false)

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, "Artist • Song", Progress(1, 4)))

        coordinator.availability.value shouldBe LibraryAvailability.Empty(MusicAccess.Granted, ScanProgress("Artist • Song", 0.25f))
    }

    @Test
    fun `a second screen checking access after the first doesn't scan again`() = runTest {
        songRepository.setSongs(emptyList())
        val coordinator = coordinator()

        coordinator.onChecked(granted = true, showRationale = false) // Home resumes first
        coordinator.onChecked(granted = true, showRationale = false) // Library resumes next

        mediaSources.scans shouldBe 1
    }
}
