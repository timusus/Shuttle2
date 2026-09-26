package com.simplecityapps.shuttle.ui.widgets

import com.simplecityapps.createSong
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NowPlayingWidgetStateTest {
    private fun stateFor(
        playbackState: PlaybackState = PlaybackState.Playing,
        shuffleMode: ShuffleMode = ShuffleMode.Off,
        repeatMode: RepeatMode = RepeatMode.Off,
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
        nowPlayingWidgetState(null, PlaybackState.Playing, ShuffleMode.On, RepeatMode.All, "/art.jpg") shouldBe
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
        stateFor(shuffleMode = ShuffleMode.On).shuffleOn shouldBe true
        stateFor(repeatMode = RepeatMode.Off).repeatMode shouldBe WidgetRepeatMode.Off
        stateFor(repeatMode = RepeatMode.All).repeatMode shouldBe WidgetRepeatMode.All
        stateFor(repeatMode = RepeatMode.One).repeatMode shouldBe WidgetRepeatMode.One
    }

    @Test
    fun `state survives a round trip through the serializer`() = runTest {
        listOf(
            NowPlayingWidgetState.Idle,
            NowPlayingWidgetState.Idle.copy(backgroundOpacity = 40),
            stateFor(shuffleMode = ShuffleMode.On, repeatMode = RepeatMode.One, artworkPath = "/art.jpg")
        ).forEach { state ->
            val output = ByteArrayOutputStream()
            NowPlayingWidgetStateSerializer.writeTo(state, output)
            NowPlayingWidgetStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray())) shouldBe state
        }
    }

    @Test
    fun `state saved before the opacity setting reads as fully opaque`() = runTest {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).apply {
            writeInt(1)
            writeBoolean(true)
            writeUTF("Title")
            writeUTF("Artist")
            writeUTF("Album")
            writeBoolean(true)
            writeBoolean(false)
            writeInt(WidgetRepeatMode.All.ordinal)
            writeUTF("/art.jpg")
            flush()
        }
        NowPlayingWidgetStateSerializer.readFrom(ByteArrayInputStream(output.toByteArray())) shouldBe
            NowPlayingWidgetState(
                hasTrack = true,
                title = "Title",
                artist = "Artist",
                album = "Album",
                isPlaying = true,
                repeatMode = WidgetRepeatMode.All,
                artworkPath = "/art.jpg",
                backgroundOpacity = 100
            )
    }

    @Test
    fun `the opacity setting carries into the idle state`() {
        nowPlayingWidgetState(null, PlaybackState.Playing, ShuffleMode.Off, RepeatMode.Off, null, backgroundOpacity = 60)
            .backgroundOpacity shouldBe 60
    }
}
