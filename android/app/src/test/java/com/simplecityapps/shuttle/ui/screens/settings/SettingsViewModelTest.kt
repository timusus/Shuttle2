package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.mediaprovider.StreamingBitrateCap
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.ui.screens.settings.backup.FakeLibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.backup.RestoreReport
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val prefs = InMemoryKeyValueStore()
    private val effects = FakeSettingsEffects()
    private val backupFlow = FakeLibraryBackupFlow()
    private val preferenceManager = GeneralPreferenceManager(prefs)
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        store = SettingsStore(prefs)
    }

    private fun viewModel() = SettingsViewModel(ObserveSetting(store), ReadSetting(store), SaveSetting(store), ReadLastScanDate(preferenceManager), ObserveLastScanDate(preferenceManager), effects, AndroidSettingsCatalog, backupFlow)

    private inline fun <reified T : SettingItem> item(key: String): T = AndroidSettingsCatalog.items.filterIsInstance<T>().first { it.key == key }

    @Test
    fun `the state starts from the stored values`() {
        store.preference(AppearanceSettings.PureBlack).value = true

        viewModel().uiState.value.value(AppearanceSettings.PureBlack) shouldBe true
    }

    @Test
    fun `a switch writes the preference and tells the app`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onSwitchChange(item(AppearanceSettings.PureBlack.key), true)
        runCurrent()

        store.preference(AppearanceSettings.PureBlack).value shouldBe true
        viewModel.uiState.value.value(AppearanceSettings.PureBlack) shouldBe true
        effects.changes shouldBe listOf(AppearanceSettings.PureBlack.key to true)
    }

    @Test
    fun `a choice stores the option's value in the existing format`() {
        viewModel().onChoiceSelect(item<SettingItem.Choice<*>>(AppearanceSettings.Theme.key), 2)

        store.preference(AppearanceSettings.Theme).value shouldBe ThemeMode.Dark
        prefs.getString(AppearanceSettings.Theme.key, null) shouldBe "2"
        effects.changes shouldBe listOf(AppearanceSettings.Theme.key to ThemeMode.Dark)
    }

    @Test
    fun `a streaming quality is stored by name and caps that network's streams`() {
        viewModel().onChoiceSelect(item<SettingItem.Choice<*>>(StreamingSettings.MeteredQuality.key), 3)

        store.preference(StreamingSettings.MeteredQuality).value shouldBe StreamingQuality.Kbps128
        store.preference(StreamingSettings.UnmeteredQuality).value shouldBe StreamingQuality.Original
        prefs.getString(StreamingSettings.MeteredQuality.key, null) shouldBe "Kbps128"
        StreamingBitrateCap(StreamingSettings(store)) { true }.maxBitrateKbps() shouldBe 128
    }

    @Test
    fun `choosing the current value changes nothing`() {
        val current = store.preference(LibrarySettings.RescanFrequency).value
        val choice = item<SettingItem.Choice<*>>(LibrarySettings.RescanFrequency.key)

        viewModel().onChoiceSelect(choice, choice.options.indexOfFirst { it.value == current })

        effects.changes shouldBe emptyList()
    }

    @Test
    fun `a slider stores each position but applies it once it settles`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        val slider = item<SettingItem.Slider<*>>(AppearanceSettings.WidgetBackgroundOpacity.key)

        viewModel.onSliderChange(slider, 40.2f)
        advanceTimeBy(SettingsViewModel.SLIDER_SETTLE_MILLIS / 2)
        viewModel.onSliderChange(slider, 60.7f)
        runCurrent()

        store.preference(AppearanceSettings.WidgetBackgroundOpacity).value shouldBe 61
        effects.changes shouldBe emptyList()

        advanceTimeBy(SettingsViewModel.SLIDER_SETTLE_MILLIS + 1)

        effects.changes shouldBe listOf(AppearanceSettings.WidgetBackgroundOpacity.key to 61)
    }

    @Test
    fun `rescan starts an import and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.Rescan)
        runCurrent()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(SettingsUiEvent.RescanStarted)
        effects.rescans shouldBe 1
    }

    @Test
    fun `clearing the artwork cache reports when it's done`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.ClearArtworkCache)
        runCurrent()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(SettingsUiEvent.ArtworkCacheCleared)
        effects.cacheClears shouldBe 1
    }

    @Test
    fun `downloading all artwork starts the download and says so`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.DownloadAllArtwork)
        runCurrent()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(SettingsUiEvent.ArtworkDownloadStarted)
        effects.artworkDownloads shouldBe 1
    }

    @Test
    fun `copying debug logs reports the result`() = runTest(mainDispatcherRule.testDispatcher) {
        effects.copyResult = CopyDebugLogsResult.TooLarge
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.CopyDebugLogs)
        runCurrent()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(SettingsUiEvent.DebugLogsCopied(CopyDebugLogsResult.TooLarge))
    }

    @Test
    fun `a handled confirmation is consumed`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.onAction(SettingsAction.Rescan)
        runCurrent()

        viewModel.onEventHandled(viewModel.uiState.value.events.single().id)
        runCurrent()

        viewModel.uiState.value.events shouldBe emptyList()
    }

    @Test
    fun `the last scan date updates as a scan finishes (#648)`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        val scanned = Instant.fromEpochMilliseconds(1_000)
        preferenceManager.lastMediaImportDate = scanned
        runCurrent()

        viewModel.uiState.value.lastScanDate shouldBe scanned
    }

    @Test
    fun `every catalog setting is in the state`() {
        val keys = viewModel().uiState.value.values.keys

        keys shouldBe AndroidSettingsCatalog.settings.map { it.key }.toSet()
    }

    private fun TestScope.events(viewModel: SettingsViewModel) = viewModel.uiState.value.events.map { it.value }

    @Test
    fun `exporting asks for a save location, then builds and writes the backup`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.ExportBackup)
        runCurrent()
        viewModel.exportBackupTo("content://dest")
        runCurrent()

        events(viewModel) shouldBe listOf(SettingsUiEvent.BackupExportRequested("shuttle-library-backup.json"), SettingsUiEvent.BackupExportSaved)
        backupFlow.written shouldBe mapOf("content://dest" to "{\"staged\":true}")
    }

    @Test
    fun `an export that can't be built or written reports failure`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        backupFlow.stagedJson = null
        viewModel.exportBackupTo("content://dest")
        backupFlow.stagedJson = "{}"
        backupFlow.writeSucceeds = false
        viewModel.exportBackupTo("content://dest")
        backupFlow.failure = IllegalStateException("boom")
        viewModel.exportBackupTo("content://dest")
        runCurrent()

        events(viewModel) shouldBe List(3) { SettingsUiEvent.BackupExportFailed }
    }

    @Test
    fun `importing reports the songs matched, playlists restored and songs not found`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        backupFlow.report = RestoreReport(songsMatched = 9, songsUnmatched = 3, statsWritten = 7, playlistsRestored = 2, playlistsUnresolved = emptyList(), membersSkipped = 0)

        viewModel.importBackupFrom("content://src")
        runCurrent()

        events(viewModel) shouldBe listOf(SettingsUiEvent.BackupImported(songsMatched = 9, playlistsRestored = 2, songsUnmatched = 3))
    }

    @Test
    fun `an import that can't be read or throws reports failure`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        backupFlow.report = null
        viewModel.importBackupFrom("content://src")
        backupFlow.failure = IllegalStateException("boom")
        viewModel.importBackupFrom("content://src")
        runCurrent()

        events(viewModel) shouldBe List(2) { SettingsUiEvent.BackupImportFailed }
    }

    @Test
    fun `cancelling an import posts nothing`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        backupFlow.failure = kotlinx.coroutines.CancellationException("cancelled")

        viewModel.importBackupFrom("content://src")
        runCurrent()

        events(viewModel) shouldBe emptyList()
    }
}
