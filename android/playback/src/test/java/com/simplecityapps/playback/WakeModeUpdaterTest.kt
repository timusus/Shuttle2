package com.simplecityapps.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.simplecityapps.playback.fakes.withFakeUriStatics
import io.kotest.matchers.shouldBe
import org.junit.Test

class WakeModeUpdaterTest {
    @Test
    fun `a local file keeps only the CPU awake`() {
        withFakeUriStatics {
            WakeModeUpdater.wakeModeFor(MediaItem.fromUri("file:///music/song.mp3")) shouldBe C.WAKE_MODE_LOCAL
            WakeModeUpdater.wakeModeFor(MediaItem.fromUri("content://media/external/audio/media/1")) shouldBe C.WAKE_MODE_LOCAL
        }
    }

    @Test
    fun `a stream keeps the Wi-Fi awake too`() {
        withFakeUriStatics {
            WakeModeUpdater.wakeModeFor(MediaItem.fromUri("https://example.com/stream.mp3")) shouldBe C.WAKE_MODE_NETWORK
            WakeModeUpdater.wakeModeFor(MediaItem.fromUri("jellyfin://item/1")) shouldBe C.WAKE_MODE_NETWORK
        }
    }
}
