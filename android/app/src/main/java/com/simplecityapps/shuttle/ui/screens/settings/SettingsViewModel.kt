package com.simplecityapps.shuttle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored value of every setting the catalog shows, keyed by preference key. */
data class SettingsUiState(
    val values: Map<String, Any?> = emptyMap(),
    val lastScanDate: Date? = null
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> value(setting: Setting<T>): T = if (values.containsKey(setting.key)) values[setting.key] as T else setting.default
}

sealed interface SettingsUiEvent {
    data object RescanStarted : SettingsUiEvent

    data object ArtworkCacheCleared : SettingsUiEvent

    data object ArtworkDownloadStarted : SettingsUiEvent

    data class DebugLogsCopied(val result: CopyDebugLogsResult) : SettingsUiEvent
}

/** Backs every settings destination: reads and writes the catalog's settings through [SettingsStore]. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val effects: SettingsEffects
) : ViewModel() {
    private val lastScanDate = MutableStateFlow(effects.lastScanDate())

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(catalogSettings.map { setting -> settingsStore.preference(setting).flow.map { setting.key to it } }) { it.toMap() },
        lastScanDate
    ) { values, lastScan -> SettingsUiState(values = values, lastScanDate = lastScan) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(values = catalogSettings.associate { it.key to settingsStore.preference(it).value }, lastScanDate = lastScanDate.value)
        )

    private val _events = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

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
        settingsStore.preference(item.setting).value = value
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
                _events.tryEmit(SettingsUiEvent.RescanStarted)
            }

            SettingsAction.ClearArtworkCache -> viewModelScope.launch {
                effects.clearArtworkCache()
                _events.emit(SettingsUiEvent.ArtworkCacheCleared)
            }

            SettingsAction.DownloadAllArtwork -> {
                effects.downloadAllArtwork()
                _events.tryEmit(SettingsUiEvent.ArtworkDownloadStarted)
            }

            SettingsAction.CopyDebugLogs -> viewModelScope.launch {
                _events.emit(SettingsUiEvent.DebugLogsCopied(effects.copyDebugLogs()))
            }
        }
    }

    /** Re-reads what isn't observable, such as the last scan date, when the screen comes back into view. */
    fun onResume() {
        lastScanDate.value = effects.lastScanDate()
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
        val preference = settingsStore.preference(setting)
        if (preference.value == value) return
        preference.value = value
        effects.onSettingChanged(setting, value)
    }

    companion object {
        const val SLIDER_SETTLE_MILLIS = 300L

        /** Every setting a catalog row stores or depends on. */
        val catalogSettings: List<Setting<*>> = SettingsCatalog.items
            .flatMap { item ->
                val stored = when (item) {
                    is SettingItem.Switch -> item.setting
                    is SettingItem.Choice<*> -> item.setting
                    is SettingItem.Slider<*> -> item.setting
                    is SettingItem.Navigate, is SettingItem.Action -> null
                }
                listOfNotNull(stored, item.dependsOn)
            }
            .distinctBy { it.key }
    }
}
