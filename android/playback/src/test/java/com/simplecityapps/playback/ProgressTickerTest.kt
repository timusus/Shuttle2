package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakeListenedPlayer
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.fakes.withFakeUriStatics
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressTickerTest {
    private val player = withFakeUriStatics { FakeListenedPlayer(listOf(testSong(1, duration = 180_000).toQueueEntry().toMediaItem())) }

    private val scope = TestScope()

    private val ticker = ProgressTicker(player, scope)

    @Test
    fun `nothing is published with no current item`() {
        player.items = emptyList()

        ticker.publish()

        ticker.progressFlow.value shouldBe null
    }

    @Test
    fun `until the item is ready, its duration is its song's`() {
        player.positionMs = 5_000

        ticker.publish()
        ticker.progressFlow.value shouldBe PlaybackProgress(5_000, 180_000)

        player.durationMs = 179_500
        ticker.publish()
        ticker.progressFlow.value shouldBe PlaybackProgress(5_000, 179_500)
    }

    @Test
    fun `while ticking, progress is published every interval, and stops when it stops`() {
        ticker.setTicking(true)
        scope.runCurrent()
        ticker.progressFlow.value shouldBe PlaybackProgress(0, 180_000)

        player.positionMs = 100
        scope.advanceTimeBy(ProgressTicker.INTERVAL_MS)
        scope.runCurrent()
        ticker.progressFlow.value shouldBe PlaybackProgress(100, 180_000)

        ticker.setTicking(false)
        ticker.ticking shouldBe false
        player.positionMs = 200
        scope.advanceTimeBy(ProgressTicker.INTERVAL_MS * 2)
        ticker.progressFlow.value shouldBe PlaybackProgress(100, 180_000)
    }
}
