package com.simplecityapps.shuttle.ui.shell.player

import com.simplecityapps.createSong
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlayerSeekTest {

    private val actions = mockk<PlayerActions>(relaxed = true)

    @Test
    fun `seekBy adds the delta to the current position`() {
        actions.seekBy(PlayerProgress(positionMs = 60_000, durationMs = 180_000), deltaSeconds = 15)

        verify { actions.seekTo(75_000) }
    }

    @Test
    fun `seekBy with a negative delta seeks backward`() {
        actions.seekBy(PlayerProgress(positionMs = 60_000, durationMs = 180_000), deltaSeconds = -10)

        verify { actions.seekTo(50_000) }
    }

    @Test
    fun `seekBy clamps to the start of the track`() {
        actions.seekBy(PlayerProgress(positionMs = 5_000, durationMs = 180_000), deltaSeconds = -30)

        verify { actions.seekTo(0) }
    }

    @Test
    fun `seekBy clamps to the end of the track`() {
        actions.seekBy(PlayerProgress(positionMs = 170_000, durationMs = 180_000), deltaSeconds = 30)

        verify { actions.seekTo(180_000) }
    }

    @Test
    fun `an audio song is not seekable`() {
        createSong(path = "/music/track.mp3").type.isSeekable shouldBe false
    }

    @Test
    fun `an audiobook song is seekable`() {
        createSong(path = "/audiobook/chapter-one.mp3").type.isSeekable shouldBe true
    }

    @Test
    fun `a podcast song is seekable`() {
        createSong(path = "/podcast/episode-one.mp3").type.isSeekable shouldBe true
    }
}
