package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.designsystem.component.ChoiceSetting
import com.simplecityapps.shuttle.designsystem.component.EqBand
import com.simplecityapps.shuttle.designsystem.component.EqualizerCurve
import com.simplecityapps.shuttle.designsystem.component.InfoSetting
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SliderSetting
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting

@Composable
fun SettingRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Group with header: link, choice, switch") {
                SettingsGroup(
                    title = "Appearance",
                    rows = listOf(
                        { ChoiceSetting("Theme", "Follow system", {}, icon = Icons.Rounded.DarkMode, shapes = it) },
                        { LinkSetting("Accent colour", {}, summary = "Artwork", icon = Icons.Rounded.Palette, shapes = it) },
                        { SwitchSetting("Dynamic colour", checked = true, onCheckedChange = {}, icon = Icons.Rounded.Palette, shapes = it) },
                    ),
                )
            },
            BoardSection("Switch: on, off, with summary") {
                SettingsGroup(
                    rows = listOf(
                        { SwitchSetting("Replay gain", checked = true, onCheckedChange = {}, shapes = it) },
                        { SwitchSetting("Gapless playback", checked = false, onCheckedChange = {}, shapes = it) },
                        {
                            SwitchSetting(
                                "Pause on disconnect",
                                checked = true,
                                onCheckedChange = {},
                                summary = "Pause when headphones or Bluetooth disconnect",
                                shapes = it,
                            )
                        },
                    ),
                )
            },
            BoardSection("Slider and info") {
                SettingsGroup(
                    rows = listOf(
                        {
                            SliderSetting(
                                "Crossfade",
                                value = 4f,
                                onValueChange = {},
                                valueRange = 0f..12f,
                                steps = 11,
                                valueLabel = "4 s",
                                icon = Icons.Rounded.Timer,
                                shapes = it,
                            )
                        },
                        { InfoSetting("Version", "2026.09.25", icon = Icons.Rounded.Info, shapes = it) },
                    ),
                )
            },
            BoardSection("Disabled") {
                SettingsGroup(
                    rows = listOf(
                        { LinkSetting("Music folders", {}, summary = "Only for the local provider", icon = Icons.Rounded.Folder, enabled = false, shapes = it) },
                        { SwitchSetting("Replay gain", checked = true, onCheckedChange = {}, enabled = false, shapes = it) },
                        {
                            SliderSetting(
                                "Pre-amp",
                                value = 0.5f,
                                onValueChange = {},
                                valueLabel = "0 dB",
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                enabled = false,
                                shapes = it,
                            )
                        },
                    ),
                )
            },
        ),
    )
}

private val frequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

@Composable
private fun Bands(gains: List<Float>, enabled: Boolean = true, labels: List<String> = frequencies) {
    Row(Modifier.fillMaxWidth()) {
        gains.forEachIndexed { i, gain -> EqBand(labels[i], gain, {}, Modifier.weight(1f), enabled = enabled) }
    }
}

@Composable
fun EqBandBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("0 dB, boosted, cut") { Bands(listOf(0f, 6f, -4.5f), labels = listOf("60 Hz", "910 Hz", "14 kHz")) },
            BoardSection("Disabled (equalizer off)") { Bands(listOf(0f, 6f, -4.5f), enabled = false, labels = listOf("60 Hz", "910 Hz", "14 kHz")) },
        ),
    )
}

@Composable
private fun CurveOverBands(gains: List<Float>, enabled: Boolean = true) {
    Column {
        EqualizerCurve(gains, Modifier.fillMaxWidth(), enabled = enabled)
        Bands(gains, enabled = enabled)
    }
}

@Composable
fun EqCurveBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Flat") { CurveOverBands(listOf(0f, 0f, 0f, 0f, 0f)) },
            BoardSection("Preset: bass boost") { CurveOverBands(listOf(8f, 5f, 0f, 0f, 0f)) },
            BoardSection("Custom") { CurveOverBands(listOf(3f, -2f, -5f, 4f, 7f)) },
            BoardSection("Equalizer off") { CurveOverBands(listOf(3f, -2f, -5f, 4f, 7f), enabled = false) },
        ),
    )
}
