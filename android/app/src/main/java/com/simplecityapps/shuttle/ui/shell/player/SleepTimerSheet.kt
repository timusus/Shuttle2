package com.simplecityapps.shuttle.ui.shell.player

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ConnectedButtonGroup
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SliderSetting
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import java.util.Locale
import kotlin.math.roundToInt

/** The preset lengths, in minutes, as the legacy dialog offered them; the slider covers the rest. */
private val SleepTimerPresets = listOf(5, 15, 30, 60)
private const val MinSleepMinutes = 5
private const val MaxSleepMinutes = 180
private const val SleepMinutesStep = 5

/** What "+5 min" adds to a running timer. */
private const val ExtendMinutes = 5

/** The sleep timer: the time left on a running one, or the length and play-to-end choice to start one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepTimerSheet(
    player: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SleepTimerSheetContent(player, actions, onDone = onDismiss)
    }
}

/** The body of [SleepTimerSheet], without the sheet. [onDone] runs once a timer starts or stops. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SleepTimerSheetContent(
    player: PlayerUiState,
    actions: PlayerActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(PlayerTestTags.SleepTimerSheet).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sleep_timer_dialog_title), style = MaterialTheme.typography.titleLarge)
        }
        if (player.sleepTimerActive) {
            RunningSleepTimer(player, actions, onDone)
        } else {
            NewSleepTimer(player, actions, onDone)
        }
    }
}

@Composable
private fun RunningSleepTimer(
    player: PlayerUiState,
    actions: PlayerActions,
    onDone: () -> Unit,
) {
    val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
    val formatMinutes = rememberMinutesFormat()
    Text(
        text = remaining?.let { if (it > 0) DateUtils.formatElapsedTime(it / 1000) else stringResource(R.string.sleep_timer_waiting_track_end) }.orEmpty(),
        style = if ((remaining ?: 0) > 0) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S2Button(
            text = stringResource(R.string.player_sleep_timer_extend, formatMinutes(ExtendMinutes)),
            onClick = { actions.startSleepTimer((remaining ?: 0) + ExtendMinutes * DateUtils.MINUTE_IN_MILLIS, player.sleepTimerPlayToEnd) },
            style = S2ButtonStyle.Tonal,
            size = S2ButtonSize.Medium,
            icon = Icons.Rounded.Add,
            modifier = Modifier.weight(1f),
        )
        S2Button(
            text = stringResource(R.string.sleep_timer_dialog_button_stop_timer),
            onClick = {
                actions.stopSleepTimer()
                onDone()
            },
            size = S2ButtonSize.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewSleepTimer(
    player: PlayerUiState,
    actions: PlayerActions,
    onDone: () -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(SleepTimerPresets[2]) }
    var playToEnd by rememberSaveable { mutableStateOf(player.sleepTimerPlayToEnd) }
    val formatMinutes = rememberMinutesFormat()
    S2ConnectedButtonGroup(
        options = SleepTimerPresets,
        selected = minutes.takeIf { it in SleepTimerPresets } ?: -1,
        onSelect = { minutes = it },
        label = formatMinutes,
        modifier = Modifier.fillMaxWidth(),
    )
    SettingsGroup(
        rows = listOf(
            { shapes ->
                SliderSetting(
                    title = stringResource(R.string.player_sleep_timer_length),
                    value = minutes.toFloat(),
                    onValueChange = { minutes = (it / SleepMinutesStep).roundToInt() * SleepMinutesStep },
                    valueRange = MinSleepMinutes.toFloat()..MaxSleepMinutes.toFloat(),
                    steps = (MaxSleepMinutes - MinSleepMinutes) / SleepMinutesStep - 1,
                    valueLabel = formatMinutes(minutes),
                    shapes = shapes,
                )
            },
            { shapes ->
                SwitchSetting(
                    title = stringResource(R.string.sleep_timer_play_to_track_end),
                    checked = playToEnd,
                    onCheckedChange = { playToEnd = it },
                    shapes = shapes,
                )
            },
        ),
    )
    S2Button(
        text = stringResource(R.string.player_sleep_timer_start),
        onClick = {
            actions.startSleepTimer(minutes * DateUtils.MINUTE_IN_MILLIS, playToEnd)
            onDone()
        },
        size = S2ButtonSize.Medium,
        icon = Icons.Rounded.Bedtime,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The running timer in the Now Playing header: its countdown, or "end of song" once it waits for the
 * track. Tapping it opens the sheet, which stops or extends it. The countdown ticks only while shown.
 */
@Composable
internal fun SleepTimerChip(
    actions: PlayerActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
    val description = stringResource(R.string.player_sleep_timer_on)
    AssistChip(
        onClick = onClick,
        label = { Text(remaining?.let { if (it > 0) DateUtils.formatElapsedTime(it / 1000) else stringResource(R.string.player_sleep_timer_track_end) }.orEmpty()) },
        modifier = modifier.testTag(PlayerTestTags.SleepTimerChip).semantics { contentDescription = description },
        leadingIcon = { Icon(Icons.Rounded.Bedtime, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

/** Formats a length in minutes as "15 min", "1 hr" or "1 hr, 30 min", in the user's locale. */
@Composable
private fun rememberMinutesFormat(): (Int) -> String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    return remember(locale) {
        val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        val formatMinutes: (Int) -> String = { minutes ->
            val hours = minutes / 60
            val rest = minutes % 60
            val measures = buildList {
                if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
                if (rest > 0 || hours == 0) add(Measure(rest, MeasureUnit.MINUTE))
            }
            format.formatMeasures(*measures.toTypedArray())
        }
        formatMinutes
    }
}
