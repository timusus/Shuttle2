package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeMediaSources
import com.simplecityapps.fakes.FakeScannerFolderStore
import com.simplecityapps.fakes.FakeServerAuthentication
import com.simplecityapps.fakes.FakeSongImportStateProvider
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.mediaprovider.SongImportState
import com.simplecityapps.mediaprovider.server.SavedServerLogin
import com.simplecityapps.shuttle.entitlement.TryAddServer
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.persistence.SourceReachability
import com.simplecityapps.shuttle.ui.screens.settings.ObserveLastScanDate
import com.simplecityapps.shuttle.ui.screens.sources.servers.ForgetServer
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SourcesViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val folderStore = FakeScannerFolderStore()
    private val importState = FakeSongImportStateProvider()
    private val preferences = GeneralPreferenceManager(InMemoryKeyValueStore())
    private val songs = FakeSongRepository()

    /** The paywall gate's answer: whether another server may be added before Pro. */
    private var serverAllowed = true

    private val emby = FakeServerAuthentication(SavedServerLogin("http://emby:8096", "tim", "secret"))

    private fun TestScope.viewModel(mediaSources: FakeMediaSources) = SourcesViewModel(
        mediaSources,
        ObserveScannerFolders(folderStore),
        AddScannerFolder(folderStore),
        RemoveScannerFolder(folderStore),
        RefreshScannerFolders(folderStore),
        importState,
        TryAddServer { serverAllowed },
        ConnectServer(mediaSources),
        ObserveLastScanDate(preferences),
        ForgetServer(mapOf(MediaProviderType.Emby to emby)),
        ObserveSongCounts(songs),
        ObserveSourceReachability(preferences),
        ObserveSourceUpdated(preferences),
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `this device and connected servers show as on`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.Plex))

        viewModel.uiState.value.thisDevice shouldBe true
        viewModel.uiState.value.usesAndroidProvider shouldBe false
        viewModel.uiState.value.servers.filter { it.connected }.map { it.type } shouldBe listOf(MediaProviderType.Plex)
    }

    @Test
    fun `the Android provider is flagged - since folder choices don't apply to it`() = runTest {
        viewModel(FakeMediaSources(MediaProviderType.MediaStore)).uiState.value.usesAndroidProvider shouldBe true
    }

    @Test
    fun `turning this device on enables the S2 scanner and scans`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onThisDeviceChange(true)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Shuttle)
        mediaSources.scans shouldBe 1
        mediaSources.folderChangeScans shouldBe 0
    }

    @Test
    fun `turning this device off disables every local provider and keeps servers`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.MediaStore, MediaProviderType.Jellyfin)
        val viewModel = viewModel(mediaSources)

        viewModel.onThisDeviceChange(false)

        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Jellyfin)
        viewModel.uiState.value.thisDevice shouldBe false
    }

    @Test
    fun `a picked folder is added and scanned - a cancelled pick does nothing`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        val viewModel = viewModel(mediaSources)

        viewModel.onFolderPicked(FolderKind.Extra, null)
        mediaSources.scans shouldBe 0

        viewModel.onFolderPicked(FolderKind.Extra, "content://tree/Hidden")
        viewModel.uiState.value.folders.extras.map { it.name } shouldBe listOf("Hidden")
        mediaSources.scans shouldBe 1
        mediaSources.folderChangeScans shouldBe 1
    }

    @Test
    fun `a folder the store refuses reports it isn't on this device`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        folderStore.refused = setOf("content://cloud/Remote")
        val viewModel = viewModel(mediaSources)

        viewModel.onFolderPicked(FolderKind.Exclude, "content://cloud/Remote")

        val event = viewModel.uiState.value.events.single()
        event.value shouldBe SourcesEvent.FolderNotOnDevice
        mediaSources.scans shouldBe 0

        viewModel.onEventHandled(event.id)
        viewModel.uiState.value.events shouldBe emptyList()
    }

    @Test
    fun `resuming re-reads the folders - for a grant revoked while away`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle))

        viewModel.onResume()

        folderStore.refreshes shouldBe 1
    }

    @Test
    fun `removing a folder rescans`() = runTest {
        val mediaSources = FakeMediaSources(MediaProviderType.Shuttle)
        val viewModel = viewModel(mediaSources)
        viewModel.onFolderPicked(FolderKind.Include, "content://tree/Music")

        viewModel.onRemoveFolder(FolderKind.Include, viewModel.uiState.value.folders.includes.single())

        viewModel.uiState.value.folders.includes shouldBe emptyList()
        // Each a folder change: the songs it takes out go at once, however many
        mediaSources.folderChangeScans shouldBe 2
    }

    @Test
    fun `a server sign-in enables it and scans - signing out disables it`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)

        viewModel.onServerConnected(MediaProviderType.Emby)
        mediaSources.enabledTypes.value shouldBe listOf(MediaProviderType.Emby)
        mediaSources.scans shouldBe 1

        viewModel.onRemoveServer(MediaProviderType.Emby)
        mediaSources.enabledTypes.value shouldBe emptyList()
    }

    @Test
    fun `removing a server forgets its address and login`() = runTest {
        val mediaSources = FakeMediaSources()
        val viewModel = viewModel(mediaSources)
        viewModel.onServerConnected(MediaProviderType.Emby)

        viewModel.onRemoveServer(MediaProviderType.Emby)

        emby.savedLogin() shouldBe SavedServerLogin()
        emby.forgottenServer shouldBe 1
    }

    @Test
    fun `a failed scan surfaces its error - cleared by the next scan`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle))

        importState.setState(SongImportState.ImportComplete(MediaProviderType.Shuttle, "Couldn't reach the server"))
        viewModel.uiState.value.scanError shouldBe "Couldn't reach the server"

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, null, null))
        viewModel.uiState.value.scanError shouldBe null
    }

    @Test
    fun `a server that couldn't be reached last session still shows as failed - until an import gets through`() = runTest {
        preferences.setSourceReachability("Jellyfin", SourceReachability("Can't reach the server", Instant.fromEpochMilliseconds(1_000)))
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Jellyfin))

        viewModel.uiState.value.servers.first { it.type == MediaProviderType.Jellyfin }.status shouldBe SourceStatus.Failed("Can't reach the server")
        viewModel.uiState.value.servers.first { it.type == MediaProviderType.Plex }.status shouldBe SourceStatus.Idle

        importState.setState(SongImportState.ImportComplete(MediaProviderType.Jellyfin, null))
        preferences.setSourceReachability("Jellyfin", SourceReachability(null, Instant.fromEpochMilliseconds(2_000)))
        viewModel.uiState.value.servers.first { it.type == MediaProviderType.Jellyfin }.status shouldBe SourceStatus.Idle
    }

    @Test
    fun `each source shows when its own import last completed`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.Jellyfin, MediaProviderType.Plex))
        val device = Instant.fromEpochMilliseconds(1_000)
        val jellyfin = Instant.fromEpochMilliseconds(2_000)
        viewModel.uiState.value.deviceUpdated shouldBe null
        viewModel.uiState.value.servers.map { it.updated } shouldBe listOf(null, null, null)

        preferences.setSourceUpdated("Shuttle", device)
        preferences.setSourceUpdated("Jellyfin", jellyfin)

        viewModel.uiState.value.deviceUpdated shouldBe device
        viewModel.uiState.value.servers.associate { it.type to it.updated } shouldBe mapOf(
            MediaProviderType.Jellyfin to jellyfin,
            MediaProviderType.Emby to null,
            MediaProviderType.Plex to null,
        )
    }

    @Test
    fun `last updated shows an import's end when it's saved after the import's last state`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Jellyfin))
        viewModel.uiState.value.lastImport shouldBe null

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Jellyfin, null, null))
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Jellyfin, null))
        val finished = Instant.fromEpochMilliseconds(1_700_000_000_000)
        preferences.lastMediaImportDate = finished

        viewModel.uiState.value.lastImport shouldBe finished
    }

    @Test
    fun `a new server needs Pro once the trial is used up`() = runTest {
        val viewModel = viewModel(FakeMediaSources())

        viewModel.onAddServer() shouldBe true

        serverAllowed = false
        viewModel.onAddServer() shouldBe false
    }

    @Test
    fun `each source shows its own song count once the library loads`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.Jellyfin))
        viewModel.uiState.value.deviceSongs shouldBe null

        songs.setSongs(
            listOf(
                createSong(id = 1),
                createSong(id = 2),
                createSong(id = 3, mediaProvider = MediaProviderType.Jellyfin),
            ),
        )

        viewModel.uiState.value.deviceSongs shouldBe 2
        viewModel.uiState.value.servers.associate { it.type to it.songs } shouldBe
            mapOf(MediaProviderType.Jellyfin to 1, MediaProviderType.Emby to 0, MediaProviderType.Plex to 0)
    }

    @Test
    fun `each source shows its own import - a server that fails while this device scans`() = runTest {
        val viewModel = viewModel(FakeMediaSources(MediaProviderType.Shuttle, MediaProviderType.Plex))

        importState.setState(SongImportState.ImportProgress(MediaProviderType.Shuttle, "Scanning", Progress(340, 1_000)))
        importState.setState(SongImportState.ImportComplete(MediaProviderType.Plex, "Couldn't reach the server"))

        viewModel.uiState.value.deviceStatus shouldBe SourceStatus.Importing(Progress(340, 1_000))
        viewModel.uiState.value.servers.first { it.type == MediaProviderType.Plex }.status shouldBe SourceStatus.Failed("Couldn't reach the server")

        importState.setState(SongImportState.ImportComplete(MediaProviderType.Shuttle, null))

        viewModel.uiState.value.deviceStatus shouldBe SourceStatus.Idle
    }
}
