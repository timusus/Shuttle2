package com.simplecityapps.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WakeModeUpdaterTest {
    @Test
    fun `a local file keeps only the CPU awake`() {
        WakeModeUpdater.wakeModeFor(MediaItem.fromUri("file:///music/song.mp3")) shouldBe C.WAKE_MODE_LOCAL
        WakeModeUpdater.wakeModeFor(MediaItem.fromUri("content://media/external/audio/media/1")) shouldBe C.WAKE_MODE_LOCAL
    }

    @Test
    fun `a stream keeps the Wi-Fi awake too`() {
        WakeModeUpdater.wakeModeFor(MediaItem.fromUri("https://example.com/stream.mp3")) shouldBe C.WAKE_MODE_NETWORK
        WakeModeUpdater.wakeModeFor(MediaItem.fromUri("jellyfin://item/1")) shouldBe C.WAKE_MODE_NETWORK
    }
}
