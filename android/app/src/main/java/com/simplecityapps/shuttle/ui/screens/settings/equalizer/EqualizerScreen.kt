package com.simplecityapps.shuttle.ui.screens.settings.equalizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.playback.dsp.equalizer.Equalizer
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ChoiceSetting
import com.simplecityapps.shuttle.designsystem.component.EqBand
import com.simplecityapps.shuttle.designsystem.component.S2ChoiceList
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.ui.screens.equalizer.FrequencyResponseChart
import com.simplecityapps.shuttle.ui.screens.settings.SettingsScaffold
import java.util.Locale

@Composable
fun EqualizerScreen(
    uiState: EqualizerUiState,
    onNavigateUp: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelect: (Equalizer.Presets.Preset) -> Unit,
    onBandGainChange: (frequency: Int, gainDb: Float) -> Unit,
    onBandGainChangeFinished: () -> Unit,
    onPreampGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var choosingPreset by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(title = stringResource(R.string.dsp_equalizer_title), onNavigateUp = onNavigateUp, modifier = modifier) {
        item(key = "controls") {
            SettingsGroup(
                rows = listOf(
                    { shapes: ListItemShapes ->
                        SwitchSetting(
                            title = stringResource(R.string.settings_equalizer_enabled),
                            checked = uiState.enabled,
                            onCheckedChange = onEnabledChange,
                            shapes = shapes
                        )
                    },
                    { shapes: ListItemShapes ->
                        ChoiceSetting(
                            title = stringResource(R.string.dsp_equalizer_hint_preset),
                            value = stringResource(uiState.selectedPreset.nameResId),
                            onClick = { choosingPreset = true },
                            enabled = uiState.enabled,
                            shapes = shapes
                        )
                    }
                )
            )
        }
        item(key = "bands") {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    FrequencyResponseChart(
                        points = uiState.frequencyResponse,
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(start = 4.dp, end = 16.dp),
                        enabled = uiState.enabled
                    )
                    if (uiState.headroomAttenuationDb < -0.05f) {
                        Caption(stringResource(R.string.settings_equalizer_headroom, String.format(Locale.getDefault(), "%.1f", -uiState.headroomAttenuationDb)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EqBand(
                            frequency = stringResource(R.string.dsp_preamp_short),
                            gainDb = uiState.preampGainDb,
                            onGainChange = onPreampGainChange,
                            modifier = Modifier.weight(1f).testTag(PREAMP_TAG),
                            enabled = uiState.enabled,
                            contentDescription = stringResource(R.string.dsp_preamp)
                        )
                        // Sets the preamp, which shifts every band, apart from the bands themselves.
                        VerticalDivider(Modifier.height(200.dp).padding(horizontal = 6.dp))
                        uiState.bands.forEach { band ->
                            EqBand(
                                frequency = frequencyLabel(band.frequency),
                                gainDb = band.gainDb,
                                onGainChange = { onBandGainChange(band.frequency, it) },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.enabled,
                                onGainChangeFinished = onBandGainChangeFinished
                            )
                        }
                    }
                    Caption(stringResource(R.string.settings_equalizer_units))
                }
            }
        }
    }

    if (choosingPreset) {
        S2Dialog(
            title = stringResource(R.string.dsp_equalizer_hint_preset),
            onDismissRequest = { choosingPreset = false },
            dismissLabel = stringResource(android.R.string.cancel)
        ) {
            S2ChoiceList(
                options = uiState.presets.map { stringResource(it.nameResId) },
                selectedIndex = uiState.presets.indexOf(uiState.selectedPreset),
                onSelect = { index ->
                    choosingPreset = false
                    onPresetSelect(uiState.presets[index])
                }
            )
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Tags the preamp column, so tests can find its slider among the bands'. */
internal const val PREAMP_TAG = "equalizerPreamp"

/** "32", "500", "1k", "16k": short enough for ten bands across a phone. */
internal fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else "$hz"
