package com.simplecityapps.shuttle.designsystem.catalog

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simplecityapps.shuttle.designsystem.component.Artwork
import com.simplecityapps.shuttle.designsystem.component.ArtworkPlaceholder
import com.simplecityapps.shuttle.designsystem.component.ArtworkSize
import com.simplecityapps.shuttle.designsystem.component.QueuePosition
import com.simplecityapps.shuttle.designsystem.component.QueueRow
import com.simplecityapps.shuttle.designsystem.component.S2MiniPlayer
import com.simplecityapps.shuttle.designsystem.component.S2NavigationBar
import com.simplecityapps.shuttle.designsystem.component.S2PlaybackProgress
import com.simplecityapps.shuttle.designsystem.component.S2PlayerControls
import com.simplecityapps.shuttle.designsystem.component.S2RepeatMode
import com.simplecityapps.shuttle.designsystem.component.S2SeekBar

@Composable
private fun MiniPlayer(playing: Boolean, buffering: Boolean = false, progress: Float = 0.35f) {
    S2MiniPlayer(
        title = "Paranoid Android",
        subtitle = "Radiohead · OK Computer",
        playing = playing,
        progress = { progress },
        onPlayPause = {},
        onNext = {},
        onClick = {},
        artwork = { Artwork(ArtworkPlaceholder.Album, size = ArtworkSize.Small, image = { SampleArt() }) },
        buffering = buffering,
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
            BoardSection("Playing (wavy)") { S2SeekBar(positionMs = 83_000, durationMs = 383_000, onSeek = {}, playing = true) },
            BoardSection("Paused (flat)") { S2SeekBar(positionMs = 83_000, durationMs = 383_000, onSeek = {}, playing = false) },
            BoardSection("Dragging") {
                S2SeekBar(
                    positionMs = 250_000,
                    durationMs = 383_000,
                    onSeek = {},
                    playing = true,
                    interactionSource = rememberHeldInteraction { DragInteraction.Start() },
                )
            },
            BoardSection("Over an hour") { S2SeekBar(positionMs = 1_830_000, durationMs = 5_025_000, onSeek = {}, playing = true) },
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
private fun Queue(title: String, subtitle: String, variant: Int, position: QueuePosition, duration: String, dragging: Boolean = false) {
    QueueRow(
        title = title,
        subtitle = subtitle,
        onClick = {},
        position = position,
        artwork = { Artwork(ArtworkPlaceholder.Song, size = ArtworkSize.Small, image = { SampleArt(variant) }) },
        duration = duration,
        dragging = dragging,
    )
}

@Composable
fun QueueRowBoard(width: BoardWidth) {
    Board(
        width,
        listOf(
            BoardSection("Played") { Queue("Airbag", "Radiohead", 0, QueuePosition.Played, "4:44") },
            BoardSection("Current") { Queue("Paranoid Android", "Radiohead", 1, QueuePosition.Current, "6:23") },
            BoardSection("Upcoming") { Queue("Subterranean Homesick Alien", "Radiohead", 2, QueuePosition.Upcoming, "4:27") },
            BoardSection("Dragging, between rows") {
                Column {
                    Queue("Exit Music (For a Film)", "Radiohead", 0, QueuePosition.Upcoming, "4:24")
                    Queue("Let Down", "Radiohead", 1, QueuePosition.Upcoming, "4:59", dragging = true)
                    Queue("Karma Police", "Radiohead", 2, QueuePosition.Upcoming, "4:21")
                }
            },
        ),
    )
}
