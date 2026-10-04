package com.simplecityapps.shuttle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.logging.Logger
import com.simplecityapps.shuttle.scrobbling.IsLastFmConfigured
import com.simplecityapps.shuttle.settings.EqualizerSettings
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.settings.backup.LibraryBackupFlow
import com.simplecityapps.shuttle.ui.screens.settings.equalizer.nameKey
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import com.simplecityapps.shuttle.ui.text.StringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored value of every setting the catalog shows, keyed by preference key, and the confirmations still to show. */
data class SettingsUiState(
    val values: Map<String, Any?> = emptyMap(),
    val lastScanDate: Instant? = null,
    val equalizerPreset: Equalizer.Presets.Preset = Equalizer.Presets.custom,
    /** False in a build without a Last.fm API key and secret, where the Scrobbling row is hidden. */
    val lastFmConfigured: Boolean = false,
    val events: List<PendingEvent<SettingsUiEvent>> = emptyList()
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> value(setting: Setting<T>): T = if (values.containsKey(setting.key)) values[setting.key] as T else setting.default

    /** What the Equalizer row shows: Off, or the preset in use. */
    val equalizerSummary: StringKey
        get() = if (value(EqualizerSettings.Enabled)) equalizerPreset.nameKey else StringKey.SETTINGS_STATE_OFF
}

sealed interface SettingsUiEvent {
    data object RescanStarted : SettingsUiEvent

    /** The UI should open a save picker; its result goes to [SettingsViewModel.exportBackupTo]. */
    data class BackupExportRequested(val suggestedName: String) : SettingsUiEvent

    data object BackupExportSaved : SettingsUiEvent

    data object BackupExportFailed : SettingsUiEvent

    /** The UI should open a file picker for a backup to restore. */
    data object BackupImportPickerRequested : SettingsUiEvent

    data class BackupImported(
        val songsMatched: Int,
        val playlistsRestored: Int,
        val songsUnmatched: Int,
        val settingsRestored: Int = 0
    ) : SettingsUiEvent

    data object BackupImportFailed : SettingsUiEvent

    data object ArtworkCacheCleared : SettingsUiEvent

    data object ArtworkDownloadStarted : SettingsUiEvent

    data class DebugLogsShared(val result: ShareDebugLogsResult) : SettingsUiEvent
}

/** Backs every settings destination: reads and writes the catalog's settings. */
@ViewModelKey(SettingsViewModel::class)
@ContributesIntoMap(AppScope::class)
class SettingsViewModel @Inject constructor(
    observeSetting: ObserveSetting,
    private val readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    readLastScanDate: ReadLastScanDate,
    observeLastScanDate: ObserveLastScanDate,
    observeEqualizerPreset: ObserveEqualizerPreset,
    readEqualizerPreset: ReadEqualizerPreset,
    isLastFmConfigured: IsLastFmConfigured,
    private val effects: SettingsEffects,
    catalog: SettingsCatalog,
    private val backupFlow: LibraryBackupFlow
) : ViewModel() {
    private val catalogSettings = catalog.settings
    private val lastFmConfigured = isLastFmConfigured()

    private val events = PendingEvents<SettingsUiEvent>()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(catalogSettings.map { setting -> observeSetting(setting).map { setting.key to it } }) { it.toMap() },
        observeLastScanDate(),
        observeEqualizerPreset(),
        events.flow
    ) { values, lastScan, preset, events -> SettingsUiState(values = values, lastScanDate = lastScan, equalizerPreset = preset, lastFmConfigured = lastFmConfigured, events = events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(values = catalogSettings.associate { it.key to readSetting(it) }, lastScanDate = readLastScanDate(), equalizerPreset = readEqualizerPreset(), lastFmConfigured = lastFmConfigured)
        )

    private val sliderEffects = mutableMapOf<String, Job>()

    fun onSwitchChange(
        item: SettingItem.Switch,
        checked: Boolean
    ) {
        write(item.setting, checked)
    }

    fun onChoiceSelect(
        item: SettingItem.Choice<*>,
        optionIndex: Int
    ) {
        select(item, optionIndex)
    }

    /** Stores every slider position straight away, but applies it once the slider settles, so a drag doesn't redraw widgets per frame. */
    fun onSliderChange(
        item: SettingItem.Slider<*>,
        position: Float
    ) {
        slide(item, position)
    }

    private fun <T : Number> slide(
        item: SettingItem.Slider<T>,
        position: Float
    ) {
        val value = item.fromFloat(position.coerceIn(item.range))
        saveSetting(item.setting, value)
        sliderEffects.remove(item.key)?.cancel()
        sliderEffects[item.key] = viewModelScope.launch {
            delay(SLIDER_SETTLE_MILLIS)
            effects.onSettingChanged(item.setting, value)
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Rescan -> {
                effects.rescan()
                events.post(SettingsUiEvent.RescanStarted)
            }

            SettingsAction.ExportBackup -> events.post(SettingsUiEvent.BackupExportRequested(BACKUP_FILE_NAME))

            SettingsAction.ImportBackup -> {
                events.post(SettingsUiEvent.BackupImportPickerRequested)
            }

            SettingsAction.ClearArtworkCache -> viewModelScope.launch {
                effects.clearArtworkCache()
                events.post(SettingsUiEvent.ArtworkCacheCleared)
            }

            SettingsAction.DownloadAllArtwork -> {
                effects.downloadAllArtwork()
                events.post(SettingsUiEvent.ArtworkDownloadStarted)
            }

            SettingsAction.ShareDebugLogs -> viewModelScope.launch {
                events.post(SettingsUiEvent.DebugLogsShared(effects.shareDebugLogs()))
            }
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)

    /** Builds the backup and writes it to the save-picker [destination]; call once per pick. */
    fun exportBackupTo(destination: String) {
        viewModelScope.launch {
            val saved = try {
                backupFlow.buildBackupJson()?.let { backupFlow.writeBackup(destination, it) } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Library backup export failed" }
                false
            }
            events.post(if (saved) SettingsUiEvent.BackupExportSaved else SettingsUiEvent.BackupExportFailed)
        }
    }

    /** Reads, parses and merges the backup at the picker [source]. */
    fun importBackupFrom(source: String) {
        viewModelScope.launch {
            val report = try {
                backupFlow.readAndRestore(source)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Library backup import failed" }
                null
            }
            events.post(
                if (report == null) {
                    SettingsUiEvent.BackupImportFailed
                } else {
                    SettingsUiEvent.BackupImported(report.songsMatched, report.playlistsRestored, report.songsUnmatched, report.settingsRestored)
                }
            )
        }
    }

    private fun <T> select(
        item: SettingItem.Choice<T>,
        optionIndex: Int
    ) {
        write(item.setting, item.options[optionIndex].value)
    }

    private fun <T> write(
        setting: Setting<T>,
        value: T
    ) {
        if (readSetting(setting) == value) return
        saveSetting(setting, value)
        effects.onSettingChanged(setting, value)
    }

    companion object {
        const val SLIDER_SETTLE_MILLIS = 300L
    }
}

private const val BACKUP_FILE_NAME = "shuttle-library-backup.json"

private val logger = Logger.tagged("SettingsViewModel")
