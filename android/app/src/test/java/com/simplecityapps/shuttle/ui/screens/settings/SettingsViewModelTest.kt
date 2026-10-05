package com.simplecityapps.shuttle.ui.screens.settings

import com.simplecityapps.mediaprovider.StreamingPolicy
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.equalizer.KeyValueEqualizerPresetStore
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.ProSource
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.entitlement.TryUseProFeature
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.scrobbling.IsLastFmConfigured
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.settings.StreamingQuality
import com.simplecityapps.shuttle.settings.StreamingSettings
import com.simplecityapps.shuttle.settings.ThemeMode
import com.simplecityapps.shuttle.settings.TranscodeFormat
import com.simplecityapps.shuttle.streaming.DeliveredFormats
import com.simplecityapps.shuttle.ui.screens.settings.backup.FakeLibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.backup.RestoreReport
import com.simplecityapps.shuttle.ui.screens.settings.model.AndroidSettingsCatalog
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsLink
import com.simplecityapps.shuttle.ui.text.StringKey
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private var lastFmConfigured = false
    private val prefs = InMemoryKeyValueStore()
    private val effects = FakeSettingsEffects()
    private val backupFlow = FakeLibraryBackupFlow()
    private val preferenceManager = GeneralPreferenceManager(prefs)
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        store = SettingsStore(prefs)
    }

    /** What the real Pro gate decides a Pro option from; Pro by default. Records each feature asked for. */
    private val entitlement = MutableStateFlow<Entitlement>(Entitlement.Pro(ProSource.Lifetime))
    private val proFeaturesAsked = mutableListOf<ProFeature>()
    private val proFeatureGate = ServerAccessGate(entitlement, startTrial = null)
    private val tryUseProFeature = TryUseProFeature { feature ->
        proFeaturesAsked += feature
        proFeatureGate.tryUse(feature)
    }

    private fun viewModel() = SettingsViewModel(ObserveSetting(store), ReadSetting(store), SaveSetting(store), ReadLastScanDate(preferenceManager), ObserveLastScanDate(preferenceManager), ObserveEqualizerPreset(KeyValueEqualizerPresetStore(prefs)), ReadEqualizerPreset(KeyValueEqualizerPresetStore(prefs)), IsLastFmConfigured { lastFmConfigured }, effects, AndroidSettingsCatalog, backupFlow, tryUseProFeature)

    private inline fun <reified T : SettingItem> item(key: String): T = AndroidSettingsCatalog.items.filterIsInstance<T>().first { it.key == key }

    @Test
    fun `the state starts from the stored values`() {
        store.preference(AppearanceSettings.PureBlack).value = true

        viewModel().uiState.value.value(AppearanceSettings.PureBlack) shouldBe true
    }

    @Test
    fun `the state says whether Last_fm is configured in this build`() {
        viewModel().uiState.value.lastFmConfigured shouldBe false

        lastFmConfigured = true
        viewModel().uiState.value.lastFmConfigured shouldBe true
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
    fun `turning ReplayGain on asks the Pro gate and turning it off never does`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        val replayGain = item<SettingItem.Choice<*>>(PlaybackSettings.ReplayGain.key)

        viewModel.onChoiceSelect(replayGain, replayGain.options.indexOfFirst { it.value == ReplayGainMode.Album })
        runCurrent()
        store.preference(PlaybackSettings.ReplayGain).value shouldBe ReplayGainMode.Album
        proFeaturesAsked shouldBe listOf(ProFeature.AdvancedAudio)

        entitlement.value = Entitlement.Free(trialUsed = true)
        viewModel.onChoiceSelect(replayGain, replayGain.options.indexOfFirst { it.value == ReplayGainMode.Off })
        runCurrent()
        store.preference(PlaybackSettings.ReplayGain).value shouldBe ReplayGainMode.Off
        proFeaturesAsked shouldBe listOf(ProFeature.AdvancedAudio)
    }

    @Test
    fun `a refused ReplayGain mode keeps the stored one after the trial`() = runTest(mainDispatcherRule.testDispatcher) {
        store.preference(PlaybackSettings.ReplayGain).value = ReplayGainMode.Track
        entitlement.value = Entitlement.Free(trialUsed = true)
        val viewModel = viewModel()
        val replayGain = item<SettingItem.Choice<*>>(PlaybackSettings.ReplayGain.key)

        viewModel.onChoiceSelect(replayGain, replayGain.options.indexOfFirst { it.value == ReplayGainMode.Album })
        runCurrent()

        store.preference(PlaybackSettings.ReplayGain).value shouldBe ReplayGainMode.Track
        effects.changes shouldBe emptyList()
    }

    @Test
    fun `a ReplayGain mode is stored while the store hasn't answered so a purchaser is never blocked`() = runTest(mainDispatcherRule.testDispatcher) {
        entitlement.value = Entitlement.Unknown
        val viewModel = viewModel()
        val replayGain = item<SettingItem.Choice<*>>(PlaybackSettings.ReplayGain.key)

        viewModel.onChoiceSelect(replayGain, replayGain.options.indexOfFirst { it.value == ReplayGainMode.Album })
        runCurrent()

        store.preference(PlaybackSettings.ReplayGain).value shouldBe ReplayGainMode.Album
    }

    @Test
    fun `a streaming quality is stored by name and caps that network's streams`() {
        viewModel().onChoiceSelect(item<SettingItem.Choice<*>>(StreamingSettings.MeteredQuality.key), 3)

        store.preference(StreamingSettings.MeteredQuality).value shouldBe StreamingQuality.Kbps128
        store.preference(StreamingSettings.UnmeteredQuality).value shouldBe StreamingQuality.Original
        prefs.getString(StreamingSettings.MeteredQuality.key, null) shouldBe "Kbps128"
        StreamingPolicy(StreamingSettings(store), DeliveredFormats()) { true }.maxBitrateKbps() shouldBe 128
    }

    @Test
    fun `a transcode format and a download quality are stored by name and read by the streaming policy`() {
        viewModel().onChoiceSelect(item<SettingItem.Choice<*>>(StreamingSettings.Format.key), 1)
        viewModel().onChoiceSelect(item<SettingItem.Choice<*>>(StreamingSettings.DownloadQuality.key), 2)

        prefs.getString(StreamingSettings.Format.key, null) shouldBe "Opus"
        prefs.getString(StreamingSettings.DownloadQuality.key, null) shouldBe "Kbps192"
        val policy = StreamingPolicy(StreamingSettings(store), DeliveredFormats()) { false }
        policy.transcodeFormat() shouldBe TranscodeFormat.Opus
        policy.downloadMaxBitrateKbps() shouldBe 192
        policy.maxBitrateKbps() shouldBe null
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
    fun `the crossfade slider stores whole seconds in milliseconds`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        val slider = item<SettingItem.Slider<*>>(PlaybackSettings.CrossfadeDuration.key)

        viewModel.onSliderChange(slider, 6000f)
        runCurrent()

        store.preference(PlaybackSettings.CrossfadeDuration).value shouldBe 6000
    }

    @Test
    fun `the crossfade slider rounds to whole seconds`() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        val slider = item<SettingItem.Slider<*>>(PlaybackSettings.CrossfadeDuration.key)

        viewModel.onSliderChange(slider, 5999.6f)
        runCurrent()

        store.preference(PlaybackSettings.CrossfadeDuration).value shouldBe 6000
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
        effects.shareResult = ShareDebugLogsResult.Empty
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onAction(SettingsAction.ShareDebugLogs)
        runCurrent()

        viewModel.uiState.value.events.map { it.value } shouldBe listOf(SettingsUiEvent.DebugLogsShared(ShareDebugLogsResult.Empty))
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

    @Test
    fun `the Equalizer row's state reaches the state, so the root can show it`() {
        AndroidSettingsCatalog.items.filterIsInstance<SettingItem.Navigate>()
            .single { it.target == SettingsLink.Equalizer }
            .stateSetting shouldBe EqualizerSettings.Enabled

        store.preference(EqualizerSettings.Enabled).value = true

        viewModel().uiState.value.value(EqualizerSettings.Enabled) shouldBe true
    }

    @Test
    fun `the Equalizer summary is Off while it is off, whatever the preset`() {
        KeyValueEqualizerPresetStore(prefs).preset = Equalizer.Presets.bassBoost

        viewModel().uiState.value.equalizerSummary shouldBe StringKey.SETTINGS_STATE_OFF
    }

    @Test
    fun `the Equalizer summary is the preset's name while it is on, and follows a new preset`() = runTest {
        store.preference(EqualizerSettings.Enabled).value = true
        val presets = KeyValueEqualizerPresetStore(prefs)
        presets.preset = Equalizer.Presets.bassBoost
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.uiState.value.equalizerSummary shouldBe StringKey.EQ_PRESET_BASS_BOOST

        presets.preset = Equalizer.Presets.custom
        runCurrent()

        viewModel.uiState.value.equalizerSummary shouldBe StringKey.EQ_PRESET_CUSTOM
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
        backupFlow.report = RestoreReport(songsMatched = 9, songsUnmatched = 3, statsWritten = 7, playlistsRestored = 2, playlistsUnresolved = emptyList(), membersSkipped = 0, settingsRestored = 14)

        viewModel.importBackupFrom("content://src")
        runCurrent()

        events(viewModel) shouldBe listOf(SettingsUiEvent.BackupImported(songsMatched = 9, playlistsRestored = 2, songsUnmatched = 3, settingsRestored = 14))
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
