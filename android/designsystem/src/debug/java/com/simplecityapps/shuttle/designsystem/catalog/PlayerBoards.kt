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
import com.simplecityapps.shuttle.fixtures.SampleLibrary
import com.simplecityapps.shuttle.fixtures.SampleSong

private val nowPlaying = SampleLibrary.album("undertow").songs[1]

@Composable
private fun MiniPlayer(playing: Boolean, buffering: Boolean = false, progress: Float = 0.35f) {
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
