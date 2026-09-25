package com.simplecityapps.shuttle.ui.shell.player

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateUtils
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.LinkSetting
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ConnectedButtonGroup
import com.simplecityapps.shuttle.designsystem.component.SettingsGroup
import com.simplecityapps.shuttle.designsystem.component.SettingsHeader
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.simplecityapps.shuttle.ui.screens.settings.EqualizerRoute
import com.simplecityapps.shuttle.ui.screens.settings.SettingsDestinationRoute
import com.simplecityapps.shuttle.ui.screens.settings.model.SettingsDestination
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

/** The sleep timer's ruler, in minutes: 5 to 3 hours in steps of 5, labelled every half hour. */
private const val SleepMinutesStep = 5
private const val SleepTicks = 36
private const val SleepLabelEvery = 6
private const val DefaultSleepMinutes = 30

/** What "+5 min" adds to a running timer. */
private const val ExtendMinutes = 5

/** The speed ruler: 0.5× to 2× in tenths, labelled every half. */
private const val MinSpeed = 0.5f
private const val SpeedStep = 0.1f
private const val SpeedTicks = 16
private const val SpeedLabelEvery = 5
private val SpeedPresets = listOf(0.8f, 1f, 1.2f, 1.5f, 2f)
private val ReplayGainModes = listOf(ReplayGainMode.Track, ReplayGainMode.Album, ReplayGainMode.Off)

/**
 * The sleep timer panel (ported from Shuttle Podcasts): the time left and +5 min / Stop on a running
 * timer, or a ruler of lengths, "End of song" and Start to begin one. The panel stays open either way,
 * so starting a timer shows its countdown in place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SleepTimerPanel(
    player: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(PlayerTestTags.SleepTimerPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelHeading(stringResource(R.string.player_sleep_timer))
        if (player.sleepTimerActive) {
            RunningSleepTimer(player, actions)
        } else {
            NewSleepTimer(player, actions)
        }
    }
}

@Composable
private fun RunningSleepTimer(
    player: PlayerUiState,
    actions: PlayerActions,
) {
    val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
    val formatMinutes = rememberMinutesFormat()
    val counting = (remaining ?: 0) > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = remaining?.let { if (it > 0) DateUtils.formatElapsedTime(it / 1000) else stringResource(R.string.sleep_timer_waiting_track_end) }.orEmpty(),
            style = if (counting) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge,
        )
        if (counting && player.sleepTimerPlayToEnd) {
            Text(stringResource(R.string.sleep_timer_play_to_track_end), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
            onClick = actions::stopSleepTimer,
            size = S2ButtonSize.Medium,
            icon = Icons.Rounded.Stop,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NewSleepTimer(
    player: PlayerUiState,
    actions: PlayerActions,
) {
    var minutes by rememberSaveable { mutableIntStateOf(DefaultSleepMinutes) }
    var playToEnd by rememberSaveable { mutableStateOf(player.sleepTimerPlayToEnd) }
    val formatMinutes = rememberMinutesFormat()
    RulerValue(formatMinutes(minutes))
    RulerSlider(
        value = minutes / SleepMinutesStep - 1,
        count = SleepTicks,
        onValueChange = { minutes = (it + 1) * SleepMinutesStep },
        contentDescription = stringResource(R.string.player_sleep_timer_length),
        stateDescription = formatMinutes(minutes),
        tickLabel = { index -> if ((index + 1) % SleepLabelEvery == 0) formatMinutes((index + 1) * SleepMinutesStep) else null },
    )
    SettingsGroup(
        rows = listOf(
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        S2Button(
            text = stringResource(R.string.player_sleep_timer_track_end),
            onClick = { actions.startSleepTimer(0, playToEnd = true) },
            style = S2ButtonStyle.Tonal,
            size = S2ButtonSize.Medium,
            modifier = Modifier.weight(1f),
        )
        S2Button(
            text = stringResource(R.string.player_sleep_timer_start),
            onClick = { actions.startSleepTimer(minutes * DateUtils.MINUTE_IN_MILLIS, playToEnd) },
            size = S2ButtonSize.Medium,
            icon = Icons.Rounded.Bedtime,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Playback & sound (owner decision 2, the speed UI ported from Shuttle Podcasts): the speed on a ruler
 * and in presets, the ReplayGain mode, and links to the Equalizer and to the rest of Settings'
 * Playback & sound, so those screens stay the one home of their settings.
 */
@Composable
internal fun PlaybackSoundPanel(
    player: PlayerUiState,
    actions: PlayerActions,
    onOpenRoute: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatSpeed = rememberSpeedFormat()
    Column(
        modifier = modifier.fillMaxWidth().testTag(PlayerTestTags.PlaybackSoundPanel).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelHeading(stringResource(R.string.settings_destination_playback_and_sound))
        RulerValue(formatSpeed(player.playbackSpeed))
        RulerSlider(
            value = ((player.playbackSpeed - MinSpeed) / SpeedStep).roundToInt(),
            count = SpeedTicks,
            onValueChange = { actions.setPlaybackSpeed(speedAt(it)) },
            contentDescription = stringResource(R.string.player_speed),
            stateDescription = formatSpeed(player.playbackSpeed),
            tickLabel = { index -> if (index % SpeedLabelEvery == 0) formatSpeed(speedAt(index)) else null },
        )
        S2ConnectedButtonGroup(
            options = SpeedPresets,
            selected = player.playbackSpeed,
            onSelect = actions::setPlaybackSpeed,
            label = formatSpeed,
            modifier = Modifier.fillMaxWidth(),
        )
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

/** Rounds to tenths, so the ruler's float steps land on 0.8 rather than 0.80000001. */
private fun speedAt(index: Int): Float = ((MinSpeed + index * SpeedStep) * 10).roundToInt() / 10f

/** The panel's title, where focus lands when it opens. */
@Composable
private fun PanelHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

/** The ruler's value, large, over the marker that points at the ruler's centre. */
@Composable
private fun RulerValue(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = text, style = MaterialTheme.typography.displaySmall)
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

private val RulerTickSpacing = 30.dp
private val RulerTickHeight = 40.dp
private val RulerTickWidth = 3.dp
private val RulerHeight = 72.dp

/**
 * A ruler of [count] ticks that slides under a fixed centre, snapping to the tick nearest it (the
 * Shuttle Podcasts range slider). Ticks fade towards the edges and [tickLabel] labels some of them.
 * The ruler is drawn, reading its offset only in the draw phase, so a drag never recomposes. TalkBack
 * sees one slider, adjustable in steps.
 */
@Composable
internal fun RulerSlider(
    value: Int,
    count: Int,
    onValueChange: (Int) -> Unit,
    contentDescription: String,
    stateDescription: String,
    tickLabel: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    val spacing = with(LocalDensity.current) { RulerTickSpacing.toPx() }
    val current = value.coerceIn(0, count - 1)
    val state = remember(count, spacing) {
        AnchoredDraggableState(initialValue = current, anchors = DraggableAnchors { repeat(count) { it at -it * spacing } })
    }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(state, positionalThreshold = { it * 0.5f }, animationSpec = spring())
    val latestValue by rememberUpdatedState(current)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(state, current) {
        if (state.settledValue != current) state.animateTo(current)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.drop(1).collect { if (it != latestValue) latestOnValueChange(it) }
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Numbers read left to right in either direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(RulerHeight)
                .semantics {
                    this.contentDescription = contentDescription
                    this.stateDescription = stateDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..(count - 1).toFloat(), steps = (count - 2).coerceAtLeast(0))
                    setProgress { target ->
                        latestOnValueChange(target.roundToInt().coerceIn(0, count - 1))
                        true
                    }
                }.anchoredDraggable(state, Orientation.Horizontal, flingBehavior = flingBehavior)
                .drawWithCache {
                    val labels = List(count) { index -> tickLabel(index)?.let { textMeasurer.measure(it, labelStyle) } }
                    val tickHeight = RulerTickHeight.toPx()
                    val tickWidth = RulerTickWidth.toPx()
                    val labelGap = 4.dp.toPx()
                    onDrawBehind {
                        val offset = state.offset.takeUnless { it.isNaN() } ?: 0f
                        val half = size.width / 2
                        for (index in 0 until count) {
                            val x = half + index * spacing + offset
                            if (x < -spacing * 3 || x > size.width + spacing * 3) continue
                            val alpha = (1f - abs(x - half) / half * 0.75f).coerceIn(0.25f, 1f)
                            drawLine(color.copy(alpha = alpha), Offset(x, 0f), Offset(x, tickHeight), strokeWidth = tickWidth, cap = StrokeCap.Round)
                            labels[index]?.let { label ->
                                drawText(label, color = color, topLeft = Offset(x - label.size.width / 2f, tickHeight + labelGap), alpha = alpha)
                            }
                        }
                    }
                },
        )
    }
}

private val replayGainLabels: Map<ReplayGainMode, String>
    @Composable get() = mapOf(
        ReplayGainMode.Track to stringResource(R.string.dsp_replay_gain_track),
        ReplayGainMode.Album to stringResource(R.string.dsp_replay_gain_album),
        ReplayGainMode.Off to stringResource(R.string.dsp_replay_gain_off),
    )

/** Formats a length in minutes as "15 min", "1 hr" or "1 hr, 30 min", in the user's locale. */
@Composable
internal fun rememberMinutesFormat(): (Int) -> String {
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

/** Formats a speed as "1×", "1.25×" or "0.5×", in the user's locale. */
@Composable
internal fun rememberSpeedFormat(): (Float) -> String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val template = stringResource(R.string.player_speed_value)
    return remember(locale, template) {
        val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
        val formatSpeed: (Float) -> String = { speed -> template.format(number.format(speed.toDouble())) }
        formatSpeed
    }
}
