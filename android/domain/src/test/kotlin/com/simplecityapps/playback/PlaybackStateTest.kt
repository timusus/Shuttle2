package com.simplecityapps.playback

import io.kotest.matchers.shouldBe
import org.junit.Test

/** Each [PlaybackState] subtype reports its own name, not the base class's. */
class PlaybackStateTest {
    @Test
    fun `each subtype's toString reports its own name`() {
        PlaybackState.Loading.toString() shouldBe "Loading"
        PlaybackState.Playing.toString() shouldBe "Playing"
        PlaybackState.Paused.toString() shouldBe "Paused"
    }
}
