package com.simplecityapps.shuttle.ui.shell.player

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BedtimeOff
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.R as DesignR
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.S2Action
import com.simplecityapps.shuttle.designsystem.component.S2ActionsSheet
import com.simplecityapps.shuttle.designsystem.component.S2ChoiceList
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2IconToggleButton
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControls
import com.simplecityapps.shuttle.designsystem.component.S2SeekBar
import com.simplecityapps.shuttle.designsystem.component.SwitchSetting
import com.squareup.phrase.Phrase

/** The collapse button, title and the player's tools: Cast, the sleep timer and the overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NowPlayingHeader(
    player: PlayerUiState,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showOverflow by rememberSaveable { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        S2IconButton(icon = Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse), onClick = onCollapse)
        Text(text = stringResource(R.string.player_now_playing), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (player.castAvailable) CastButton()
        S2IconToggleButton(
            icon = Icons.Rounded.BedtimeOff,
            checkedIcon = Icons.Rounded.Bedtime,
            contentDescription = stringResource(if (player.sleepTimerActive) R.string.player_sleep_timer_on else R.string.sleep_timer_dialog_title),
            checked = player.sleepTimerActive,
            onCheckedChange = { showSleepTimer = true },
        )
        S2IconButton(icon = Icons.Rounded.MoreVert, contentDescription = stringResource(DesignR.string.ds_more_options), onClick = { showOverflow = true })
    }
    if (showSleepTimer) {
        SleepTimerDialog(player = player, actions = actions, onDismiss = { showSleepTimer = false })
    }
    val current = player.current
    if (showOverflow && current != null) {
        S2ActionsSheet(
            title = current.title,
            subtitle = current.artist,
            artwork = { SongArtwork(current.song) },
            actions = listOf(
                S2Action(label = stringResource(R.string.menu_title_sort_clear_queue), onClick = actions::clearQueue, icon = Icons.Rounded.ClearAll, destructive = true),
            ),
            onDismissRequest = { showOverflow = false },
        )
    }
}

/** The Cast framework's route button, themed to the player's scheme. Only shown where Cast can start. */
@Composable
private fun CastButton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_cast)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { button ->
                CastButtonFactory.setUpMediaRouteButton(context.applicationContext, button)
                button.contentDescription = description
            }
        },
        update = { button ->
            ContextCompat.getDrawable(button.context, androidx.mediarouter.R.drawable.mr_button_light)?.mutate()?.let { drawable ->
                drawable.setTint(tint)
                button.setRemoteIndicatorDrawable(drawable)
            }
        },
        modifier = modifier.size(48.dp),
    )
}

/** The artwork, as large as fits, over the title, artist and favourite toggle. */
@Composable
internal fun NowPlayingArtwork(
    player: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    val current = player.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            if (current != null) {
                SongArtwork(current.song, Modifier.widthIn(max = 480.dp).aspectRatio(1f, matchHeightConstraintsFirst = true), size = ArtworkSize.Hero)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = current?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(current?.artist, current?.album).joinToString(" • "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            S2IconToggleButton(
                icon = Icons.Rounded.FavoriteBorder,
                checkedIcon = Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.menu_title_favorite),
                checked = player.favourite,
                onCheckedChange = { actions.toggleFavourite() },
                enabled = current != null,
            )
        }
    }
}

/**
 * The seek bar over the transport controls: the head that stays above the queue at the Queue level.
 * The controls take a fixed 336 dp, so they sit closer to the edges than the seek bar to fit the 360 dp pane.
 */
@Composable
internal fun Transport(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().height(TransportHeight),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SeekBar(player, progress, actions)
        Box(Modifier.padding(horizontal = 12.dp)) {
            S2PlayerControls(
                playing = player.playing,
                onPlayPause = actions::togglePlayback,
                onPrevious = actions::skipToPrevious,
                onNext = actions::skipToNext,
                shuffle = player.shuffle,
                onShuffleChange = { actions.toggleShuffle() },
                repeatMode = player.repeatMode,
                onRepeatClick = actions::cycleRepeatMode,
                buffering = player.buffering,
            )
        }
    }
}

/** Reads the ticking position in its own scope, so only the seek bar recomposes with it. */
@Composable
private fun SeekBar(
    player: PlayerUiState,
    progress: () -> PlayerProgress,
    actions: PlayerActions,
) {
    val current = progress()
    S2SeekBar(
        positionMs = current.positionMs,
        durationMs = current.durationMs,
        onSeek = actions::seekTo,
        playing = player.playing,
        enabled = player.current != null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

private val SleepTimerDurations = listOf(
    R.string.sleep_timer_5_minutes to 5 * DateUtils.MINUTE_IN_MILLIS,
    R.string.sleep_timer_15_minutes to 15 * DateUtils.MINUTE_IN_MILLIS,
    R.string.sleep_timer_30_minutes to 30 * DateUtils.MINUTE_IN_MILLIS,
    R.string.sleep_timer_1_hour to DateUtils.HOUR_IN_MILLIS,
)

/** Sets the sleep timer, or shows the time left on a running one and stops it. */
@Composable
internal fun SleepTimerDialog(
    player: PlayerUiState,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    val remaining by remember(actions) { actions.sleepTimerRemaining() }.collectAsState(initial = null)
    val running = remaining != null || player.sleepTimerActive
    val title = stringResource(R.string.sleep_timer_dialog_title)
    val close = stringResource(R.string.sleep_timer_dialog_button_close)
    if (running) {
        S2Dialog(
            title = title,
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sleep_timer_dialog_button_stop_timer),
            onConfirm = {
                actions.stopSleepTimer()
                onDismiss()
            },
            dismissLabel = close,
            destructive = true,
            icon = Icons.Rounded.Bedtime,
        ) {
            Text(remaining.remainingText(), style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        var selected by rememberSaveable { mutableIntStateOf(0) }
        var playToEnd by rememberSaveable { mutableStateOf(player.sleepTimerPlayToEnd) }
        S2Dialog(
            title = title,
            onDismissRequest = onDismiss,
            confirmLabel = stringResource(R.string.sleep_timer_dialog_button_set_time),
            onConfirm = {
                actions.startSleepTimer(SleepTimerDurations[selected].second, playToEnd)
                onDismiss()
            },
            dismissLabel = close,
            icon = Icons.Rounded.Bedtime,
        ) {
            Column {
                S2ChoiceList(options = SleepTimerDurations.map { stringResource(it.first) }, selectedIndex = selected, onSelect = { selected = it })
                SwitchSetting(title = stringResource(R.string.sleep_timer_play_to_track_end), checked = playToEnd, onCheckedChange = { playToEnd = it })
            }
        }
    }
}

@Composable
private fun Long?.remainingText(): String = when {
    this == null -> ""
    this <= 0L -> stringResource(R.string.sleep_timer_waiting_track_end)
    else -> Phrase.from(stringResource(R.string.sleep_timer_time_remaining)).put("time", DateUtils.formatElapsedTime(this / 1000)).format().toString()
}
