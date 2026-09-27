package com.simplecityapps.playback

import com.simplecityapps.playback.queue.song
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PlaybackPolicyTest {
    @Test
    fun `podcasts resume a little before where they were left - music from the start`() {
        PlaybackPolicy.startOf(song(1, path = "/podcast/1.mp3", playbackPosition = 60_000)) shouldBe 55_000
        PlaybackPolicy.startOf(song(1, path = "/podcast/1.mp3", playbackPosition = 3_000)) shouldBe 0
        PlaybackPolicy.startOf(song(1, playbackPosition = 60_000)) shouldBe 0
    }

    @Test
    fun `the last moments of a song count as its end`() {
        PlaybackPolicy.isNearEnd(positionMs = 199_900, durationMs = 200_000) shouldBe true
        PlaybackPolicy.isNearEnd(positionMs = 199_800, durationMs = 200_000) shouldBe false
    }
}
