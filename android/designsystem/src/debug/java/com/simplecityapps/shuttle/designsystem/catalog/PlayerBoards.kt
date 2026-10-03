package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2ExpandableSheetScaffold
import com.simplecityapps.shuttle.designsystem.component.S2IconButton
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayer
import com.simplecityapps.shuttle.designsystem.component.S2NavigationBar
import com.simplecityapps.shuttle.designsystem.component.S2PanelSheet
import com.simplecityapps.shuttle.designsystem.component.S2PlaybackProgress
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControls
import com.simplecityapps.shuttle.designsystem.component.S2SeekBar
import com.simplecityapps.shuttle.designsystem.component.S2SheetHandle
import com.simplecityapps.shuttle.designsystem.component.SectionHeader
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SampleSong
import com.simplecityapps.shuttle.ui.shell.player.QueuePosition
import com.simplecityapps.shuttle.ui.shell.player.S2RepeatMode

private val nowPlaying = SampleLibrary.album("undertow").songs[1]

@Composable
private fun MiniPlayer(playing: Boolean, buffering: Boolean = false, progress: Float = 0.35f, castingTo: String? = null) {
    S2MiniPlayer(
        title = nowPlaying.title,
        subtitle = "${nowPlaying.artist} · ${nowPlaying.album}",
        playing = playing,
        progress = { progress },
        onPlayPause = {},
        onNext = {},
        onClick = {},
        artwork = { Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Small, image = { SampleArt(nowPlaying.albumId) }) },
        buffering = buffering,
        castingTo = castingTo,
    )
}

@Composable
fun MiniPlayerBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Playing") { MiniPlayer(playing = true) },
            BoardSection("Paused") { MiniPlayer(playing = false, progress = 0.7f) },
            BoardSection("Loading (buffering)") { MiniPlayer(playing = true, buffering = true, progress = 0f) },
            BoardSection("Casting") { MiniPlayer(playing = true, castingTo = "Living Room TV") },
            BoardSection("Above the navigation bar") {
                Column {
                    MiniPlayer(playing = true)
                    S2NavigationBar(navItems(), selectedIndex = 1, onSelect = {})
                }
            },
        ),
    )
}

@Composable
private fun Controls(playing: Boolean, buffering: Boolean = false, shuffle: Boolean = false, repeatMode: S2RepeatMode = S2RepeatMode.Off) {
    S2PlayerControls(
        playing = playing,
        onPlayPause = {},
        onPrevious = {},
        onNext = {},
        shuffle = shuffle,
        onShuffleChange = {},
        repeatMode = repeatMode,
        onRepeatClick = {},
        modifier = Modifier.fillMaxWidth(),
        buffering = buffering,
    )
}

@Composable
fun PlayerControlsBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Playing (pause shown)") { Controls(playing = true) },
            BoardSection("Paused (play shown)") { Controls(playing = false) },
            BoardSection("Buffering") { Controls(playing = true, buffering = true) },
            BoardSection("Shuffle on, repeat all") { Controls(playing = true, shuffle = true, repeatMode = S2RepeatMode.All) },
            BoardSection("Repeat one") { Controls(playing = false, repeatMode = S2RepeatMode.One) },
        ),
    )
}

@Composable
fun SeekBarBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Resting") { S2SeekBar(positionMs = 83_000, durationMs = 383_000, onSeek = {}) },
            BoardSection("Disabled") { S2SeekBar(positionMs = 0, durationMs = 0, onSeek = {}, enabled = false) },
            BoardSection("Buffering") { S2SeekBar(positionMs = 83_000, durationMs = 383_000, onSeek = {}, buffering = true) },
            BoardSection("Dragging") {
                S2SeekBar(
                    positionMs = 250_000,
                    durationMs = 383_000,
                    onSeek = {},
                    interactionSource = rememberHeldInteraction { DragInteraction.Start() },
                )
            },
            BoardSection("Over an hour") { S2SeekBar(positionMs = 1_830_000, durationMs = 5_025_000, onSeek = {}) },
        ),
    )
}

@Composable
fun ProgressBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Playing (wavy)") { S2PlaybackProgress(progress = { 0.4f }, playing = true, modifier = Modifier.fillMaxWidth()) },
            BoardSection("Paused (flat)") { S2PlaybackProgress(progress = { 0.4f }, playing = false, modifier = Modifier.fillMaxWidth()) },
            BoardSection("Indeterminate (loading)") { S2PlaybackProgress(progress = null, playing = true, modifier = Modifier.fillMaxWidth()) },
        ),
    )
}

@Composable
private fun Queue(song: SampleSong, position: QueuePosition, dragging: Boolean = false) {
    QueueRow(
        title = song.title,
        subtitle = song.artist,
        onClick = {},
        position = position,
        artwork = { Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Small, image = { SampleArt(song.albumId) }) },
        duration = song.duration,
        dragging = dragging,
    )
}

private val queue = SampleLibrary.queue(6)

@Composable
fun QueueRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Played") { Queue(queue[0], QueuePosition.Played) },
            BoardSection("Current") { Queue(queue[1], QueuePosition.Current) },
            BoardSection("Upcoming") { Queue(queue[2], QueuePosition.Upcoming) },
            BoardSection("Dragging, between rows") {
                Column {
                    Queue(queue[3], QueuePosition.Upcoming)
                    Queue(queue[4], QueuePosition.Upcoming, dragging = true)
                    Queue(queue[5], QueuePosition.Upcoming)
                }
            },
        ),
    )
}

/** A Now Playing at [height]: the handle, artwork, title, seek bar and transport over a bar, with the queue panel open while [queueOpen]. */
@Composable
private fun PlayerSheetSample(queueOpen: Boolean, height: Dp, modifier: Modifier = Modifier) {
    Surface(modifier.height(height), color = MaterialTheme.colorScheme.surfaceContainer) {
        S2ExpandableSheetScaffold(
            expanded = queueOpen,
            handle = { S2SheetHandle(onClick = {}, onClickLabel = "Collapse player") },
            bottomBar = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    S2IconButton(Icons.Rounded.GraphicEq, "Playback & sound", onClick = {})
                    S2IconButton(Icons.Rounded.Bedtime, "Sleep timer", onClick = {})
                    S2Button(
                        text = "Queue",
                        onClick = {},
                        style = if (queueOpen) S2ButtonStyle.Tonal else S2ButtonStyle.Text,
                        size = S2ButtonSize.Small,
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    )
                    S2IconButton(Icons.Rounded.MoreVert, "More options", onClick = {})
                }
            },
            expandedContent = {
                S2PanelSheet(onDismiss = {}) {
                    SectionHeader(title = "Up next", containerColor = Color.Transparent)
                    queue.drop(2).forEach { Queue(it, QueuePosition.Upcoming) }
                }
            },
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(S2Spacing.medium), contentAlignment = Alignment.Center) {
                Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Hero, modifier = Modifier.aspectRatio(1f), image = { SampleArt(nowPlaying.albumId) })
            }
            Column(Modifier.padding(horizontal = S2Spacing.medium)) {
                Text(nowPlaying.title, style = MaterialTheme.typography.headlineSmall)
                Text("${nowPlaying.artist} · ${nowPlaying.album}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                S2SeekBar(positionMs = 83_000, durationMs = 383_000, onSeek = {})
            }
            S2PlayerControls(
                playing = true,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                shuffle = false,
                onShuffleChange = {},
                repeatMode = S2RepeatMode.Off,
                onRepeatClick = {},
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = S2Spacing.medium),
            )
        }
    }
}

@Composable
fun PlayerSheetBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Sheet handle") { S2SheetHandle(onClick = {}, onClickLabel = "Collapse player") },
            BoardSection("Expanded, no panel") { PlayerSheetSample(queueOpen = false, height = PhoneSheetHeight) },
            BoardSection("Queue open: the player pushed up above it") { PlayerSheetSample(queueOpen = true, height = PhoneSheetHeight) },
        ),
    )
}

@Composable
fun PlayerPaneBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Pane (360 dp)") { PlayerSheetSample(queueOpen = false, height = PaneHeight, modifier = Modifier.width(PaneWidth)) },
            BoardSection("Pane, queue open") { PlayerSheetSample(queueOpen = true, height = PaneHeight, modifier = Modifier.width(PaneWidth)) },
        ),
    )
}

private val PhoneSheetHeight = 840.dp
private val PaneHeight = 800.dp
private val PaneWidth = 360.dp
