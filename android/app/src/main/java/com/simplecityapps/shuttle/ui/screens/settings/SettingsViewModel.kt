package com.simplecityapps.shuttle.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.settings.ObserveSetting
import com.simplecityapps.shuttle.settings.ReadSetting
import com.simplecityapps.shuttle.settings.SaveSetting
import com.simplecityapps.shuttle.settings.Setting
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingItem
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsAction
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The stored value of every setting the catalog shows, keyed by preference key, and the confirmations still to show. */
data class SettingsUiState(
    val values: Map<String, Any?> = emptyMap(),
    val lastScanDate: Date? = null,
    val events: List<PendingEvent<SettingsUiEvent>> = emptyList()
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

/** Backs every settings destination: reads and writes the catalog's settings. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSetting: ObserveSetting,
    private val readSetting: ReadSetting,
    private val saveSetting: SaveSetting,
    private val effects: SettingsEffects
) : ViewModel() {
    private val lastScanDate = MutableStateFlow(effects.lastScanDate())

    private val events = PendingEvents<SettingsUiEvent>()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(catalogSettings.map { setting -> observeSetting(setting).map { setting.key to it } }) { it.toMap() },
        lastScanDate,
        events.flow
    ) { values, lastScan, events -> SettingsUiState(values = values, lastScanDate = lastScan, events = events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(values = catalogSettings.associate { it.key to readSetting(it) }, lastScanDate = lastScanDate.value)
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

            SettingsAction.ClearArtworkCache -> viewModelScope.launch {
                effects.clearArtworkCache()
                events.post(SettingsUiEvent.ArtworkCacheCleared)
            }

            SettingsAction.DownloadAllArtwork -> {
                effects.downloadAllArtwork()
                events.post(SettingsUiEvent.ArtworkDownloadStarted)
            }

            SettingsAction.CopyDebugLogs -> viewModelScope.launch {
                events.post(SettingsUiEvent.DebugLogsCopied(effects.copyDebugLogs()))
            }
        }
    }

    fun onEventHandled(id: Long) = events.consume(id)

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
        if (readSetting(setting) == value) return
        saveSetting(setting, value)
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
                listOfNotNull(stored, item.dependsOn, (item as? SettingItem.Choice<*>)?.overriddenBy?.setting)
            }
            .distinctBy { it.key }
    }
}
