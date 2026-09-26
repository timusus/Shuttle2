package com.simplecityapps.playback.spec

import com.simplecityapps.playback.AppPlayer
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.chromecast.CastMediaItemConverter
import com.simplecityapps.playback.chromecast.CastQueue
import com.simplecityapps.playback.chromecast.CastStreams
import com.simplecityapps.playback.chromecast.CastWindow.BEFORE
import com.simplecityapps.playback.chromecast.CastWindow.SIZE
import com.simplecityapps.playback.chromecast.FakeCastPlayer
import com.simplecityapps.playback.chromecast.FakeMediaInfoProvider
import com.simplecityapps.playback.chromecast.FakeReceiver
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Playback while casting, through the real playback stack: the Cast player stood in for by one that hands over as
 * Media3's does, to a receiver that reports what it's sent only once it arrives and never reports an end.
 */
@RunWith(RobolectricTestRunner::class)
class CastSpecTest {
    private val streams = CastStreams(FakeMediaInfoProvider(), EmptyCoroutineContext)

    private val converter = CastMediaItemConverter({ "10.0.0.2" }, streams, "Unknown")

    private val receiver = FakeReceiver(converter)

    private lateinit var castQueue: CastQueue

    private lateinit var castPlayer: FakeCastPlayer

    private lateinit var appPlayer: AppPlayer

    private var castPlayersBuilt = 0

    private val harness =
        PlaybackHarness(
            castQueue = { local -> CastQueue(local, converter, streams) { receiver.finished }.also { castQueue = it } },
            activePlayer = { local ->
                AppPlayer(local) {
                    castPlayersBuilt++
                    FakeCastPlayer(local, receiver, castQueue).also { castPlayer = it }.also(castQueue::attach)
                }.also { appPlayer = it }
            }
        )

    private val playback = harness.playbackOperations

    private val queue = harness.queueOperations

    @After
    fun tearDown() {
        harness.release()
    }

    private fun songs(count: Int) = (1L..count).map { song(it) }

    /** Loads [count] songs at the one at [index], at [positionMs], playing if [play], with Cast attached first if [attachCast]. */
    private fun start(
        count: Int,
        index: Int = 0,
        positionMs: Int = 0,
        play: Boolean = true,
        attachCast: Boolean = true
    ) {
        if (attachCast) appPlayer.attachCast()
        harness.run { queue.setQueue(songs(count), position = index) }
        var loaded = false
        playback.load(positionMs) { loaded = true }
        harness.runUntil { loaded && playback.playbackStateFlow.value == PlaybackState.Paused }
        if (play) {
            playback.play()
            harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        }
    }

    /** Starts casting, and lets the receiver report what it was sent. */
    private fun connect() {
        appPlayer.attachCast()
        castPlayer.connect()
        settle()
    }

    /** Lets the receiver report everything sent to it until nothing more is sent. */
    private fun settle() {
        harness.idle()
        while (receiver.hasUndelivered) {
            receiver.deliver()
            harness.idle()
        }
    }

    private val currentSongId get() = queue.getCurrentItem()?.song?.id

    @Test
    fun `attaching Cast while playing leaves playback as it was, and casting works after`() {
        start(count = 3, index = 1, positionMs = 1_000, attachCast = false)
        val sessionId = harness.audioEffectSessionManager.sessionId
        val abandons = harness.audioFocus.abandons

        appPlayer.attachCast()
        appPlayer.attachCast()
        harness.idle()

        castPlayersBuilt shouldBe 1
        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
        currentSongId shouldBe 2L
        harness.audioFocus.abandons shouldBe abandons
        harness.audioEffectSessionManager.sessionId shouldBe sessionId
        playback.pause()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Paused }
        playback.play()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        connect()

        receiver.songIds shouldBe listOf(1L, 2L, 3L)
        receiver.currentMediaItemIndex shouldBe 1
        receiver.playWhenReady shouldBe true
    }

    @Test
    fun `casting sends the queue around the current song, at its position, playing, and stops the local player`() {
        start(count = 5, index = 2, positionMs = 1_000)

        castPlayer.connect()
        harness.idle()
        receiver.deliver()
        harness.idle()

        receiver.songIds shouldBe (1L..5L).toList()
        receiver.sentItems.map { it.media?.contentUrl } shouldBe (1L..5L).map { "http://10.0.0.2:5000/${streams.key}/songs/$it/audio" }
        receiver.sentItems.map { it.media?.contentType }.distinct() shouldBe listOf("audio/wav")
        receiver.currentMediaItemIndex shouldBe 2
        receiver.currentPosition shouldBe 1_000L
        receiver.playWhenReady shouldBe true
        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
        currentSongId shouldBe 3L
    }

    @Test
    fun `casting gives up audio focus and the effect session, and coming back takes them up again`() {
        start(count = 2)
        val sessionId = harness.audioEffectSessionManager.sessionId
        val abandons = harness.audioFocus.abandons

        connect()

        harness.audioFocus.abandons shouldBe abandons + 1
        harness.audioEffectSessionManager.sessionId.shouldBeNull()

        // Playing on the receiver takes no focus on this device.
        val requestsWhileCasting = harness.audioFocus.requests
        playback.pause()
        settle()
        playback.play()
        settle()

        receiver.playWhenReady shouldBe true
        harness.audioFocus.requests shouldBe requestsWhileCasting
        harness.audioFocus.abandons shouldBe abandons + 1

        castPlayer.disconnect()
        harness.idle()

        harness.audioEffectSessionManager.sessionId shouldBe sessionId

        val requests = harness.audioFocus.requests
        playback.play()
        harness.idle()

        harness.audioFocus.requests shouldBeGreaterThan requests
    }

    @Test
    fun `a queue change while casting is sent on to the receiver`() {
        start(count = 3)
        connect()

        harness.run { queue.addToQueue(listOf(song(4), song(5))) }
        settle()
        receiver.songIds shouldBe (1L..5L).toList()

        queue.remove(listOf(queue.getQueue().single { it.song.id == 2L }))
        settle()
        receiver.songIds shouldBe listOf(1L, 3L, 4L, 5L)
        receiver.currentMediaItemIndex shouldBe 0
    }

    @Test
    fun `the receiver moving on moves the current song with it`() {
        start(count = 5)
        connect()

        receiver.playOnToNext()
        settle()
        currentSongId shouldBe 2L

        receiver.skipTo(3)
        settle()
        currentSongId shouldBe 4L
        receiver.currentMediaItemIndex shouldBe 3
    }

    @Test
    fun `a long queue is sent a window at a time, topped up and trimmed as the receiver plays on`() {
        start(count = 150)
        connect()
        receiver.songIds shouldBe (1L..SIZE).toList()

        repeat(SIZE - 5) {
            receiver.playOnToNext()
            settle()
        }

        currentSongId shouldBe (SIZE - 4).toLong()
        val current = receiver.songIds[receiver.currentMediaItemIndex]
        current shouldBe currentSongId
        receiver.songIds.last() shouldBe 150L
        receiver.songIds.size shouldBe receiver.songIds.distinct().size
        (receiver.currentMediaItemIndex <= SIZE - BEFORE) shouldBe true
        receiver.songIds shouldBe (receiver.songIds.first()..150L).toList()
    }

    @Test
    fun `RS-40 under repeat-all, the receiver plays on from the last song to the first`() {
        harness.run { queue.setQueue(songs(150), position = 145) }
        queue.setRepeatMode(RepeatMode.All)
        var loaded = false
        playback.load { loaded = true }
        harness.runUntil { loaded && playback.playbackStateFlow.value == PlaybackState.Paused }
        playback.play()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        connect()

        receiver.songIds shouldBe (136L..150L) + (1L..85L)
        repeat(5) {
            receiver.playOnToNext()
            settle()
        }
        currentSongId shouldBe 1L
        receiver.songIds[receiver.currentMediaItemIndex] shouldBe 1L
    }

    @Test
    fun `under repeat-all, a receiver holding the whole queue repeats it by itself`() {
        start(count = 3)
        queue.setRepeatMode(RepeatMode.All)
        connect()
        receiver.skipTo(2)
        settle()

        receiver.playOnToNext()
        settle()

        currentSongId shouldBe 1L
        receiver.songIds shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `RS-39 the receiver playing out the last song ends the queue as the local player does`() {
        start(count = 2)
        connect()
        val ended = harness.record(playback.trackEndedFlow)
        receiver.playOnToNext()
        settle()

        receiver.playOut()
        harness.idle()

        // The first ended as the receiver played on from it, the last as the receiver played it out.
        ended shouldBe listOf(song(1), song(2))
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        receiver.playWhenReady shouldBe false
    }

    @Test
    fun `a receiver stopped from elsewhere, or idle before the last song, doesn't end the queue`() {
        start(count = 2)
        connect()
        val ended = harness.record(playback.trackEndedFlow)

        receiver.playOut()
        harness.idle()
        receiver.playOnToNext()
        settle()
        receiver.stopFromElsewhere()
        harness.idle()

        // Only as the receiver played on from the first.
        ended shouldBe listOf(song(1))
        receiver.playWhenReady shouldBe true
    }

    @Test
    fun `a receiver going idle while a new queue is on its way doesn't end the queue`() {
        start(count = 2)
        connect()
        receiver.playOnToNext()
        settle()
        val ended = harness.record(playback.trackEndedFlow)

        harness.run { queue.setQueue(listOf(song(7), song(8))) }
        var loaded = false
        playback.load { loaded = true }
        harness.idle()
        receiver.playOut()
        harness.idle()
        settle()

        ended.shouldBeEmpty()
        loaded shouldBe true
        receiver.songIds shouldBe listOf(7L, 8L)
    }

    @Test
    fun `coming back plays on locally from where the receiver was, paused, and saves that position`() {
        start(count = 3)
        connect()
        receiver.skipTo(1)
        settle()
        receiver.playTo(1_500)
        harness.idle()

        castPlayer.disconnect()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Paused }

        currentSongId shouldBe 2L
        playback.getProgress() shouldBe 1_500
        harness.playbackPreferenceManager.playbackPosition shouldBe 1_500
    }

    @Test
    fun `a pause while casting, before the receiver reports a position, saves nothing over the one playback left`() {
        start(count = 3, positionMs = 1_000)
        playback.pause()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Paused }
        playback.play()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        val left = checkNotNull(harness.playbackPreferenceManager.playbackPosition)
        left shouldBeGreaterThan 0

        castPlayer.connect()
        harness.idle()
        playback.pause()
        harness.idle()

        harness.playbackPreferenceManager.playbackPosition shouldBe left
    }

    @Test
    fun `casting and coming back before the receiver reports never saves position zero`() {
        start(count = 3, positionMs = 0, play = false)
        harness.playbackPreferenceManager.playbackPosition = 1_234

        castPlayer.connect()
        harness.idle()
        castPlayer.disconnect()
        harness.idle()

        harness.playbackPreferenceManager.playbackPosition shouldBe 1_234
    }
}
