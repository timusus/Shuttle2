package com.simplecityapps.shuttle.ui.tile

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.ui.tile.PlaybackTileState.TapAction
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlaybackTileStateTest {
    @Test
    fun `playing a queued song is active, shows its title and toggles`() {
        PlaybackTileState.from(PlaybackState.Playing, "Song", hasQueue = true) shouldBe
            PlaybackTileState(isActive = true, subtitle = "Song", tapAction = TapAction.TogglePlayback)
    }

    @Test
    fun `paused is inactive but keeps the title`() {
        PlaybackTileState.from(PlaybackState.Paused, "Song", hasQueue = true) shouldBe
            PlaybackTileState(isActive = false, subtitle = "Song", tapAction = TapAction.TogglePlayback)
    }

    @Test
    fun `loading is not active`() {
        PlaybackTileState.from(PlaybackState.Loading, "Song", hasQueue = true).isActive shouldBe false
    }

    @Test
    fun `an empty queue is inactive with no subtitle and opens the app`() {
        PlaybackTileState.from(PlaybackState.Playing, "Stale", hasQueue = false) shouldBe
            PlaybackTileState(isActive = false, subtitle = null, tapAction = TapAction.OpenApp)
    }

    @Test
    fun `a blank title gives no subtitle`() {
        PlaybackTileState.from(PlaybackState.Paused, " ", hasQueue = true).subtitle shouldBe null
        PlaybackTileState.from(PlaybackState.Paused, null, hasQueue = true).subtitle shouldBe null
    }
}
