package com.simplecityapps.shuttle.ui.shell.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2ConnectedButtonGroup
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SettingsHeader
import com.simplecityapps.shuttle.designsystem.component.SliderSetting
import com.simplecityapps.shuttle.ui.screens.settings.EqualizerRoute
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private const val MinSpeed = 0.5f
private const val MaxSpeed = 2f

/** The slider moves in twentieths: 0.05× steps. */
private const val SpeedStepsPerUnit = 20
private val SpeedPresets = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val ReplayGainModes = listOf(ReplayGainMode.Track, ReplayGainMode.Album, ReplayGainMode.Off)

/**
 * Playback & sound from Now Playing (owner decision 2): the speed and ReplayGain mode in place, and
 * links to the Equalizer and to the rest of Settings' Playback & sound (pre-amp, USB DAC output), so
 * those screens stay the one home of their settings. A link settles the player to Mini and opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSoundSheet(
    player: PlayerUiState,
    actions: PlayerActions,
    onOpenRoute: (NavKey) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PlaybackSoundSheetContent(
            player = player,
            actions = actions,
            onOpenRoute = { route ->
                onDismiss()
                onOpenRoute(route)
            },
        )
    }
}

/** The body of [PlaybackSoundSheet], without the sheet. */
@Composable
internal fun PlaybackSoundSheetContent(
    player: PlayerUiState,
    actions: PlayerActions,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatSpeed = rememberSpeedFormat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.PlaybackSoundSheet)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_destination_playback_and_sound), style = MaterialTheme.typography.titleLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsGroup(
                rows = listOf(
                    { shapes ->
                        SliderSetting(
                            title = stringResource(R.string.player_speed),
                            value = player.playbackSpeed,
                            onValueChange = { actions.setPlaybackSpeed((it * SpeedStepsPerUnit).roundToInt().toFloat() / SpeedStepsPerUnit) },
                            valueRange = MinSpeed..MaxSpeed,
                            steps = ((MaxSpeed - MinSpeed) * SpeedStepsPerUnit).roundToInt() - 1,
                            valueLabel = formatSpeed(player.playbackSpeed),
                            icon = Icons.Rounded.Speed,
                            shapes = shapes,
                        )
                    },
                ),
            )
            S2ConnectedButtonGroup(
                options = SpeedPresets,
                selected = player.playbackSpeed,
                onSelect = actions::setPlaybackSpeed,
                label = formatSpeed,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column {
            SettingsHeader(stringResource(R.string.dsp_replay_gain_title))
            val replayGainLabels = replayGainLabels
            S2ConnectedButtonGroup(
                options = ReplayGainModes,
                selected = player.replayGainMode,
                onSelect = actions::setReplayGainMode,
                label = replayGainLabels::getValue,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsGroup(
            rows = listOf(
                { shapes ->
                    LinkSetting(
                        title = stringResource(R.string.dsp_equalizer_title),
                        onClick = { onOpenRoute(EqualizerRoute) },
                        icon = Icons.Rounded.Equalizer,
                        shapes = shapes,
                    )
                },
                { shapes ->
                    LinkSetting(
                        title = stringResource(R.string.player_more_sound_settings),
                        summary = stringResource(R.string.player_more_sound_settings_summary),
                        onClick = { onOpenRoute(SettingsDestinationRoute(SettingsDestination.PlaybackAndSound)) },
                        icon = Icons.Rounded.Settings,
                        shapes = shapes,
                    )
                },
            ),
        )
    }
}

private val replayGainLabels: Map<ReplayGainMode, String>
    @Composable get() = mapOf(
        ReplayGainMode.Track to stringResource(R.string.dsp_replay_gain_track),
        ReplayGainMode.Album to stringResource(R.string.dsp_replay_gain_album),
        ReplayGainMode.Off to stringResource(R.string.dsp_replay_gain_off),
    )

/** A speed other than normal, in the Now Playing header: "1.25×". Tapping it opens Playback & sound. */
@Composable
internal fun PlaybackSpeedChip(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = rememberSpeedFormat()(speed)
    val description = stringResource(R.string.player_speed_description, label)
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier.testTag(PlayerTestTags.PlaybackSpeedChip).semantics { contentDescription = description },
        leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** Formats a speed as "1×", "1.25×" or "0.5×", in the user's locale. */
@Composable
private fun rememberSpeedFormat(): (Float) -> String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val template = stringResource(R.string.player_speed_value)
    return remember(locale, template) {
        val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
        val formatSpeed: (Float) -> String = { speed -> template.format(number.format(speed.toDouble())) }
        formatSpeed
    }
}
