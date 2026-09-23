package com.simplecityapps.shuttle.ui.widgets

import com.simplecityapps.createSong
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NowPlayingWidgetStateTest {
    private fun stateFor(
        playbackState: PlaybackState = PlaybackState.Playing,
        shuffleMode: QueueManager.ShuffleMode = QueueManager.ShuffleMode.Off,
        repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.Off,
        artworkPath: String? = null
    ) = nowPlayingWidgetState(
        song = createSong(name = "Title", albumArtist = "Album Artist", album = "Album"),
        playbackState = playbackState,
        shuffleMode = shuffleMode,
        repeatMode = repeatMode,
        artworkPath = artworkPath
    )

    @Test
    fun `no song is the idle state`() {
        nowPlayingWidgetState(null, PlaybackState.Playing, QueueManager.ShuffleMode.On, QueueManager.RepeatMode.All, "/art.jpg") shouldBe
            NowPlayingWidgetState.Idle
    }

    @Test
    fun `maps the song's details`() {
        stateFor(artworkPath = "/art.jpg") shouldBe
            NowPlayingWidgetState(
                hasTrack = true,
                title = "Title",
                artist = "Album Artist",
                album = "Album",
                isPlaying = true,
                artworkPath = "/art.jpg"
            )
    }

    @Test
    fun `paused offers play, loading offers pause`() {
        stateFor(playbackState = PlaybackState.Paused).isPlaying shouldBe false
        stateFor(playbackState = PlaybackState.Loading).isPlaying shouldBe true
    }

    @Test
    fun `maps shuffle and repeat`() {
        stateFor(shuffleMode = QueueManager.ShuffleMode.On).shuffleOn shouldBe true
        stateFor(repeatMode = QueueManager.RepeatMode.Off).repeatMode shouldBe WidgetRepeatMode.Off
        stateFor(repeatMode = QueueManager.RepeatMode.All).repeatMode shouldBe WidgetRepeatMode.All
        stateFor(repeatMode = QueueManager.RepeatMode.One).repeatMode shouldBe WidgetRepeatMode.One
    }

    @Test
    fun `state survives a round trip through the serializer`() = runTest {
        listOf(
            NowPlayingWidgetState.Idle,
            stateFor(shuffleMode = QueueManager.ShuffleMode.On, repeatMode = QueueManager.RepeatMode.One, artworkPath = "/art.jpg")
        ).forEach { state ->
            val output = ByteArrayOutputStream()
            NowPlayingWidgetStateSerializer.writeTo(state, output)
            NowPlayingWidgetStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray())) shouldBe state
        }
    }
}
