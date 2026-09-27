package com.simplecityapps.playback.dsp.crossfade

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.FakeMediaPeriod
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.playback.spec.ClockDriver
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
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

    /** The crossfades skipped, with their reasons, in order. */
    private val skips = mutableListOf<CrossfadeSkip>()

    private val crossfade = Crossfade(player, mixer, tails, { CROSSFADE_MS }, skips::add)

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

    /** Queues A and B with A's period held unprepared, and clips A to its tail. */
    private fun queueWithAClipped() {
        unprepared += a.uid
        queue(a, b)
        driver.runUntil { player.duration != C.TIME_UNSET }
        tails.land(a)
        driver.idle()
        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
    }

    /** Plays A on until B takes over. */
    private fun playIntoB() {
        player.play()
        driver.runUntil(limitMs = 2L * SONG_MS) { player.currentMediaItemIndex == 1 }
    }

    /** Crossfade has stood down: A plays whole, the mixer has no plans and no tail is being decoded. */
    private fun shouldHaveStoodDown() {
        player.getMediaItemAt(0).clipEndMs() shouldBe C.TIME_END_OF_SOURCE
        mixer.plans.shouldBeEmpty()
        tails.decoding shouldBe null
    }

    private fun audioTrack(encoding: Int) = AudioSink.AudioTrackConfig(encoding, 44_100, 12, false, false, 4096)

    private fun allowOffload() {
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(AudioOffloadPreferences.Builder().setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED).build())
                .build()
    }

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
    fun `a clipped item shows the song's whole duration, and ends at the clip`() {
        unprepared += a.uid
        queue(a, b)
        driver.runUntil { player.duration != C.TIME_UNSET }
        player.duration shouldBe SONG_MS.toLong()

        tails.land(a)
        driver.idle()

        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        player.duration shouldBe SONG_MS.toLong()
        player.currentTimeline.getPeriod(0, Timeline.Period()).durationMs shouldBe SONG_MS - CROSSFADE_MS
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

    @Test
    fun `a song whose tail never lands in time plays into the next, reported as late`() {
        queue(a, b)

        playIntoB()

        skips shouldBe listOf(CrossfadeSkip.TailLate)
    }

    @Test
    fun `a tail that lands once the playing song is loaded to its end is reported as a short song loaded first`() {
        queue(a, b)
        driver.runUntil { sources[b.uid]?.createdMediaPeriods?.size == 1 }
        tails.land(a)

        playIntoB()

        skips shouldBe listOf(CrossfadeSkip.ShortSongLoaded)
    }

    @Test
    fun `a song whose tail can't be decoded is reported with the decoder's reason`() {
        queue(a, b)
        tails.fail(a, CrossfadeSkip.DecodeFailed)

        playIntoB()

        skips shouldBe listOf(CrossfadeSkip.DecodeFailed)
    }

    @Test
    fun `no skip is reported where no crossfade was wanted`() {
        val sameAlbumA = testSong(1, duration = SONG_MS, album = "Album").toQueueEntry()
        val sameAlbumB = testSong(2, duration = SONG_MS, album = "Album").toQueueEntry()
        queue(sameAlbumA, sameAlbumB)

        playIntoB()

        skips.shouldBeEmpty()
    }

    @Test
    fun `float output stands crossfade down, unclipping the playing item, and 16-bit output brings it back`() {
        queueWithAClipped()

        crossfade.onAudioTrackInitialized(audioTrack(C.ENCODING_PCM_FLOAT))
        driver.idle()
        shouldHaveStoodDown()

        crossfade.onAudioTrackInitialized(audioTrack(C.ENCODING_PCM_16BIT))
        tails.land(a)
        driver.idle()
        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        mixer.plans shouldContainKey a.uid
    }

    @Test
    fun `a song played into the next on float output is reported as skipped for float output`() {
        queue(a, b)
        crossfade.onAudioTrackInitialized(audioTrack(C.ENCODING_PCM_FLOAT))
        driver.idle()
        tails.decoding shouldBe null

        playIntoB()

        skips shouldBe listOf(CrossfadeSkip.FloatOutput)
    }

    @Test
    fun `allowing audio offload mid-song stands crossfade down`() {
        queueWithAClipped()

        allowOffload()
        driver.idle()

        shouldHaveStoodDown()
    }

    @Test
    fun `with audio offload allowed from the start, no tail is decoded and the skip is reported as offload`() {
        allowOffload()
        queue(a, b)
        driver.idle()
        tails.decoding shouldBe null

        playIntoB()

        skips shouldBe listOf(CrossfadeSkip.Offload)
    }

    @Test
    fun `casting stands crossfade down, and the receiver playing on is reported as skipped for Cast`() {
        queueWithAClipped()
        val cast = FakeRemote(listOf(a, b).map { it.toMediaItem() })
        crossfade.followCast(cast)

        cast.connect()
        driver.idle()
        shouldHaveStoodDown()

        cast.playOnToNext()
        driver.idle()
        skips shouldBe listOf(CrossfadeSkip.Cast)
    }

    @Test
    fun `the app player moving on before casting reports nothing for Cast`() {
        queue(a, b)
        val cast = FakeRemote(listOf(a, b).map { it.toMediaItem() })
        crossfade.followCast(cast)

        cast.playOnToNext()
        driver.idle()

        skips.shouldBeEmpty()
    }

    @Test
    fun `casting brings back the item's real duration and period, not just the clip end`() {
        queueWithAClipped()
        val cast = FakeRemote(listOf(a, b).map { it.toMediaItem() })
        crossfade.followCast(cast)

        cast.connect()
        driver.idle()

        player.duration shouldBe SONG_MS.toLong()
        player.currentTimeline.getPeriod(0, Timeline.Period()).durationMs shouldBe SONG_MS.toLong()
    }

    @Test
    fun `a seek near the real end, past the clip, is clamped by the clipped period and cuts to the next song`() {
        // Clipped from the start (no Crossfade tail dance needed): the window still shows the whole duration, same as
        // Crossfade leaves it once a tail lands, so a seek near the real end targets a period position past the clip.
        val clippedA = a.toMediaItem().buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(SONG_MS - CROSSFADE_MS).build()).build()
        player.setMediaItems(listOf(clippedA, b.toMediaItem()))
        player.prepare()
        driver.runUntil { player.duration != C.TIME_UNSET }
        player.duration shouldBe SONG_MS.toLong()

        player.seekTo(0, SONG_MS - 100L)
        player.play()
        driver.runUntil(limitMs = 2L * SONG_MS) { player.currentMediaItemIndex == 1 }
    }

    @Test
    fun `the last item in the queue is clipped for its fade-out, still shows its whole duration, and ends at the clip`() {
        unprepared += a.uid
        queue(a)
        driver.runUntil { player.duration != C.TIME_UNSET }
        player.duration shouldBe SONG_MS.toLong()

        tails.land(a)
        driver.idle()

        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        player.duration shouldBe SONG_MS.toLong()
        player.currentTimeline.getPeriod(0, Timeline.Period()).durationMs shouldBe SONG_MS - CROSSFADE_MS
        mixer.plans.getValue(a.uid).next shouldBe CrossfadePlan.Next.FadeOut
        skips.shouldBeEmpty()
    }

    @Test
    fun `repeat-one plans a plain unfaded join at the loop point, and reports no skip when it loops`() {
        player.repeatMode = Player.REPEAT_MODE_ONE
        queue(a, b)
        driver.runUntil { tails.decoding == a.uid }
        tails.land(a)
        driver.idle()

        player.getMediaItemAt(0).clipEndMs() shouldBe SONG_MS - CROSSFADE_MS
        mixer.plans.getValue(a.uid).next shouldBe CrossfadePlan.Next.Join

        var loopedToRepeat = 0
        player.addListener(
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) loopedToRepeat++
                }
            }
        )

        player.play()
        driver.runUntil(limitMs = 2L * SONG_MS) { loopedToRepeat > 0 }

        player.currentMediaItemIndex shouldBe 0
        skips.shouldBeEmpty()
    }

    /**
     * Builds each item's source from its song's duration, its period starting with its window (as a progressive
     * stream's does), with samples 100 ms apart across it (so the renderers read through an item as it plays, not
     * straight to its end), and remembers it by entry.
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
                    .setWindowPositionInFirstPeriodUs(0)
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
        private var pending: Triple<MediaItem, Long, (TailOutcome) -> Unit>? = null

        /** The uid of the entry whose tail is being decoded, or null. */
        val decoding get() = pending?.first?.queueEntry?.uid

        override fun decode(
            item: MediaItem,
            clipEndMs: Long,
            onDecoded: (TailOutcome) -> Unit
        ) {
            pending = Triple(item, clipEndMs, onDecoded)
        }

        override fun cancel() {
            pending = null
        }

        /** Finishes the decode of [entry]'s tail, in progress, with a silent tail. */
        fun land(entry: QueueEntry) {
            val (_, clipEndMs, onDecoded) = take(entry)
            val startUs = (clipEndMs - 500) * 1000
            val frames = ((entry.song.duration * 1000L - startUs) * 44_100 / 1_000_000).toInt()
            onDecoded(TailOutcome.Ready(Tail(entry.uid, 44_100, 2, startUs, clipEndMs * 1000, FloatArray(frames * 2), decodeNanos = 1)))
        }

        /** Finishes the decode of [entry]'s tail, in progress, without one, for [reason]. */
        fun fail(
            entry: QueueEntry,
            reason: CrossfadeSkip
        ) {
            take(entry).third(TailOutcome.Missing(reason))
        }

        private fun take(entry: QueueEntry): Triple<MediaItem, Long, (TailOutcome) -> Unit> {
            val decode = checkNotNull(pending) { "No decode in progress" }
            check(decode.first.queueEntry.uid == entry.uid) { "The decode in progress is ${decode.first.mediaId}'s" }
            pending = null
            return decode
        }
    }

    /**
     * Stands in for the app player: playing [items] locally until [connect], then on a remote device, where it plays on
     * to the next item by itself ([playOnToNext]).
     */
    private class FakeRemote(private val items: List<MediaItem>) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var remote = false

        private var index = 0

        private var autoTransition = false

        fun connect() {
            remote = true
            invalidateState()
        }

        fun playOnToNext() {
            index++
            autoTransition = true
            invalidateState()
        }

        override fun getState(): State = State
            .Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.map { MediaItemData.Builder(it.queueEntry.uid).setMediaItem(it).build() })
            .setCurrentMediaItemIndex(index)
            .setDeviceInfo(DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build())
            .apply {
                if (autoTransition) {
                    autoTransition = false
                    setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0)
                }
            }.build()

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
