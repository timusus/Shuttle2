package com.simplecityapps.playback.dsp.crossfade

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeMediaPeriod
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.playback.spec.ClockDriver
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val CROSSFADE_MS = 2_000L

private const val SONG_MS = 10_000

/**
 * When [Crossfade] clips the playing item, on a real player over fake sources that load on the playback thread, with
 * tails that land when the test says.
 */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer.
@RunWith(RobolectricTestRunner::class)
class CrossfadeClipTest {
    private val clock = FakeClock(false)

    /** The source built for each entry, by uid. */
    private val sources = mutableMapOf<Long, FakeMediaSource>()

    /** Entries whose source holds its media period unprepared, so the player never loads past it. */
    private val unprepared = mutableSetOf<Long>()

    private val player: ExoPlayer =
        TestExoPlayerBuilder(RuntimeEnvironment.getApplication())
            .setClock(clock)
            .setMediaSourceFactory(CrossfadeClippingMediaSourceFactory(FakeSourceFactory()))
            .build()

    private val driver = ClockDriver(clock, player)

    private val tails = FakeTails()

    private val mixer = CrossfadeMixer()

    private val crossfade = Crossfade(player, mixer, tails) { CROSSFADE_MS }

    private val a = testSong(1, duration = SONG_MS).toQueueEntry()

    private val b = testSong(2, duration = SONG_MS).toQueueEntry()

    @After
    fun tearDown() {
        crossfade.release()
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun queue(vararg entries: QueueEntry) {
        player.setMediaItems(entries.map { it.toMediaItem() })
        crossfade.attach()
        player.prepare()
    }

    private fun MediaItem.clipEndMs() = clippingConfiguration.endPositionMs

    @Test
    fun `a tail that lands once the next item is preloaded leaves the playing item whole and the preload alone`() {
        queue(a, b)
        // A loads to its end at once, so the player queues B's period after it.
        driver.runUntil { sources[b.uid]?.createdMediaPeriods?.size == 1 }

        tails.land(a)
        driver.runUntil { clock.elapsedRealtime() >= 2_000 }

        // Clipping A would have changed its period's duration, and the player would have dropped B's period for a new one.
        sources.getValue(b.uid).createdMediaPeriods.size shouldBe 1
        player.getMediaItemAt(0).clipEndMs() shouldBe C.TIME_END_OF_SOURCE
        mixer.plans shouldNotContainKey a.uid
    }

    @Test
    fun `the playing item is clipped while the player is still loading it`() {
        unprepared += a.uid
        queue(a, b)
        driver.runUntil { player.duration != C.TIME_UNSET }

        tails.land(a)
        driver.idle()

        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        mixer.plans shouldContainKey a.uid
    }

    @Test
    fun `the next item is clipped once its tail lands, keeping its preloaded period`() {
        queue(a, b)
        driver.runUntil { sources[b.uid]?.createdMediaPeriods?.size == 1 }
        tails.land(a)
        driver.idle()

        tails.land(b)
        driver.runUntil { clock.elapsedRealtime() >= 2_000 }

        player.getMediaItemAt(1).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        mixer.plans shouldContainKey b.uid
        sources.getValue(b.uid).createdMediaPeriods.size shouldBe 1
    }

    /**
     * Builds each item's source from its song's duration, with samples 100 ms apart across it (so the renderers read
     * through an item as it plays, not straight to its end), and remembers it by entry.
     */
    private inner class FakeSourceFactory : MediaSource.Factory {
        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val entry = mediaItem.queueEntry
            val durationUs = entry.song.duration * 1000L
            val window =
                FakeTimeline.TimelineWindowDefinition
                    .Builder()
                    .setUid(entry.uid)
                    .setDurationUs(durationUs)
                    .setMediaItem(mediaItem)
                    .build()
            return FakeMediaSource
                .Builder()
                .setTimeline(FakeTimeline(window))
                .setFormats(ExoPlayerTestRunner.AUDIO_FORMAT)
                .setTrackDataFactory(FakeMediaPeriod.TrackDataFactory.samplesWithRateDurationAndKeyframeInterval(0, 10f, durationUs, 1))
                .build()
                .apply {
                    setCanUpdateMediaItems(true)
                    setPeriodDefersOnPreparedCallback(entry.uid in unprepared)
                    sources[entry.uid] = this
                }
        }

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

        override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = this
    }

    /** Takes one decode at a time, as [TailDecoder] does, and finishes it only when the test lands its tail. */
    private class FakeTails : TailSource {
        private var pending: Triple<MediaItem, Long, (Tail?) -> Unit>? = null

        override fun decode(
            item: MediaItem,
            clipEndMs: Long,
            onDecoded: (Tail?) -> Unit
        ) {
            pending = Triple(item, clipEndMs, onDecoded)
        }

        override fun cancel() {
            pending = null
        }

        /** Finishes the decode of [entry]'s tail, in progress, with a silent tail. */
        fun land(entry: QueueEntry) {
            val (item, clipEndMs, onDecoded) = checkNotNull(pending) { "No decode in progress" }
            check(item.queueEntry.uid == entry.uid) { "The decode in progress is ${item.mediaId}'s" }
            pending = null
            val startUs = (clipEndMs - 500) * 1000
            val frames = ((entry.song.duration * 1000L - startUs) * 44_100 / 1_000_000).toInt()
            onDecoded(Tail(entry.uid, 44_100, 2, startUs, clipEndMs * 1000, FloatArray(frames * 2), decodeNanos = 1))
        }
    }
}
