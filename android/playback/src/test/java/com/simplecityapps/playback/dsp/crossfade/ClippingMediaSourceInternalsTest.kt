package com.simplecityapps.playback.dsp.crossfade

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeMediaPeriod
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.spec.ClockDriver
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val DURATION_MS = 10_000L

/**
 * Pins the Media3 internals [CrossfadeClippingMediaSourceFactory] relies on (#566), so an upgrade that changes one of
 * them fails a test here rather than playback: a [ClippingMediaSource] takes a new clip on its existing media period
 * without re-preparing; changing a queued period's duration drops every period Media3 already queued after it
 * (`MediaPeriodQueue.updateQueuedPeriods`/`removeAfter`, see [Crossfade.clipBlock]); and a [ClippingMediaSource]
 * refreshes its timeline from its child's, reverting an item updated in place once that timeline refreshes again for
 * an unrelated reason (why [UpdatedItemMediaSource] exists).
 */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer.
@RunWith(RobolectricTestRunner::class)
class ClippingMediaSourceInternalsTest {
    private val clock = FakeClock(false)

    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(clock).build()

    private val driver = ClockDriver(clock, player)

    @After
    fun tearDown() {
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** A timeline for [item], with its period starting at the window (as a progressive stream's does). */
    private fun timelineFor(item: MediaItem): FakeTimeline = FakeTimeline(
        FakeTimeline.TimelineWindowDefinition
            .Builder()
            .setUid(item.mediaId)
            .setDurationUs(DURATION_MS * 1000)
            .setWindowPositionInFirstPeriodUs(0)
            .setMediaItem(item)
            .build()
    )

    /** A source over [timeline] that can update its item in place. */
    private fun fakeSource(timeline: FakeTimeline): FakeMediaSource = FakeMediaSource
        .Builder()
        .setTimeline(timeline)
        .setFormats(ExoPlayerTestRunner.AUDIO_FORMAT)
        .setTrackDataFactory(FakeMediaPeriod.TrackDataFactory.samplesWithRateDurationAndKeyframeInterval(0, 10f, DURATION_MS * 1000, 1))
        .build()
        .apply { setCanUpdateMediaItems(true) }

    private fun fakeSource(item: MediaItem): FakeMediaSource = fakeSource(timelineFor(item))

    private fun clip(
        source: MediaSource,
        endMs: Long
    ): ClippingMediaSource = ClippingMediaSource.Builder(source).setEndPositionMs(endMs).setEnableClippingInMediaPeriod(true).build()

    private fun clippingConfig(endMs: Long) = MediaItem.ClippingConfiguration.Builder().setEndPositionMs(endMs).build()

    @Test
    fun `a new clip takes effect on the existing media period, without re-preparing it`() {
        val item = MediaItem.Builder().setMediaId("a").build()
        val child = fakeSource(item)

        player.setMediaSource(clip(child, 8_000))
        player.prepare()
        driver.runUntil { child.createdMediaPeriods.size == 1 }
        player.duration shouldBe 8_000L

        player.replaceMediaItem(0, item.buildUpon().setClippingConfiguration(clippingConfig(6_000)).build())
        driver.idle()

        player.duration shouldBe 6_000L
        child.createdMediaPeriods.size shouldBe 1
    }

    @Test
    fun `changing a queued period's duration drops the period Media3 already queued after it`() {
        val itemA = MediaItem.Builder().setMediaId("a").build()
        val childA = fakeSource(itemA)
        val childB = fakeSource(MediaItem.Builder().setMediaId("b").build())

        player.setMediaSources(listOf(clip(childA, 8_000), childB))
        player.prepare()
        driver.runUntil { childB.createdMediaPeriods.size == 1 }

        player.replaceMediaItem(0, itemA.buildUpon().setClippingConfiguration(clippingConfig(6_000)).build())
        driver.idle()

        childB.createdMediaPeriods.size shouldBe 2
    }

    @Test
    fun `a ClippingMediaSource reverts to the child's original item once its timeline refreshes again`() {
        val original = MediaItem.Builder().setMediaId("a").setMediaMetadata(MediaMetadata.Builder().setTitle("Original").build()).build()
        val originalTimeline = timelineFor(original)
        val child = fakeSource(originalTimeline)

        player.setMediaSource(clip(child, 8_000))
        player.prepare()
        driver.idle()

        val renamed = original.buildUpon().setMediaMetadata(MediaMetadata.Builder().setTitle("Renamed").build()).build()
        player.replaceMediaItem(0, renamed)
        driver.idle()
        player.getMediaItemAt(0).mediaMetadata.title shouldBe "Renamed"

        // The child refreshes its timeline again for some unrelated reason, still carrying the item it was prepared with.
        child.setNewSourceInfo(originalTimeline)
        driver.runUntil { player.getMediaItemAt(0).mediaMetadata.title == "Original" }
    }
}
