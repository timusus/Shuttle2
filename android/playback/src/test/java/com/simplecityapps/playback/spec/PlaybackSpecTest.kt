package com.simplecityapps.playback.spec

import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.BYTES_PER_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_2S_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.resourceUri
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.unreadableSong
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.unresolvableSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The behaviour spec in docs/testing/playback-behaviour-spec.md: one test per RS rule, on the real ExoPlayer,
 * driven only through PlaybackOperations and QueueOperations. The audio-output rules are in [AudioOutputSpecTest].
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackSpecTest {
    private val harness = PlaybackHarness()
    private val playback = harness.playbackOperations
    private val queue = harness.queueOperations

    @After
    fun tearDown() {
        harness.release()
    }

    @Test
    fun `RS-01 a song that plays to its end moves the queue on, and progress follows the new song`() {
        val first = song(1)
        val second = song(2, file = TONE_1S)
        val ended = harness.record(playback.trackEndedFlow)
        val progress = harness.record(playback.progressFlow)

        harness.run { playback.addToQueue(listOf(first, second)) }
        harness.runUntil { ended.isNotEmpty() }
        val progressBeforeTrackEnd = progress.size
        harness.runUntil { ended.size == 2 }

        ended shouldBe listOf(first, second)
        queue.queueStateFlow.value.currentItem?.song shouldBe second
        // Once the first song has ended, progress is only ever the second song's.
        progress.drop(progressBeforeTrackEnd).filterNotNull().forEach { it.duration shouldBe TONE_1S_MS }
    }

    @Test
    fun `RS-02 a skip anchors at the new song's start straight away`() {
        loadPaused(listOf(song(1), song(2)), positionMs = 1_500)
        playback.positionAnchorFlow.value.positionMs shouldBe 1_500

        playback.skipToNext()

        playback.positionAnchorFlow.value.positionMs shouldBe 0
        queue.queueStateFlow.value.currentPosition shouldBe 1
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        playback.positionAnchorFlow.value.positionMs!!.shouldBeBetween(0, TONE_2S_MS)
    }

    @Test
    fun `RS-03 a seek while paused moves the published progress and anchor, and playback resumes from it`() {
        loadPaused(listOf(song(1)))
        val ended = harness.record(playback.trackEndedFlow)

        playback.seekTo(1_200)
        harness.idle()

        playback.progressFlow.value shouldBe PlaybackProgress(1_200, TONE_2S_MS)
        playback.positionAnchorFlow.value.positionMs shouldBe 1_200
        playback.positionAnchorFlow.value.state shouldBe PlaybackState.Paused

        harness.clearAudioOutput()
        playback.play()
        harness.runUntil { ended.isNotEmpty() }
        harness.audioOutput().size.shouldBeBetween((TONE_2S_MS - 1_200 - 50) * BYTES_PER_MS, (TONE_2S_MS - 1_200 + 50) * BYTES_PER_MS)
    }

    @Test
    fun `RS-04 pausing gives up audio focus`() {
        val audioManager = shadowOf(harness.audioManager)
        harness.run { playback.addToQueue(listOf(song(1), song(2))) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        audioManager.lastAudioFocusRequest.shouldNotBeNull()
        audioManager.lastAbandonedAudioFocusRequest.shouldBeNull()

        playback.pause()
        harness.idle()

        audioManager.lastAbandonedAudioFocusRequest.shouldNotBeNull()
        harness.audioFocus.abandons shouldBe 1
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-05 removing the only queued song stops playback and gives up audio focus`() {
        val audioManager = shadowOf(harness.audioManager)
        harness.run { playback.addToQueue(listOf(song(1))) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        playback.removeQueueItem(queue.queueStateFlow.value.currentItem!!)
        harness.idle()

        queue.queueStateFlow.value.items.shouldBeEmpty()
        audioManager.lastAbandonedAudioFocusRequest.shouldNotBeNull()
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-06 songs added with shuffle on join the end of the queue in the order they were chosen`() {
        val songs = (1L..5L).map { song(it) }
        val added = (6L..8L).map { song(it) }
        harness.run { playback.shuffle(songs) {} }

        harness.run { playback.addToQueue(added) }

        queue.shuffleModeFlow.value shouldBe QueueManager.ShuffleMode.On
        queue.queueStateFlow.value.items.map { it.song }.takeLast(3) shouldBe added
        queue.getQueue(QueueManager.ShuffleMode.Off).map { it.song }.takeLast(3) shouldBe added
    }

    @Test
    fun `RS-07 a saved shuffle order holding a song twice is restored as saved`() {
        val a = song(1)
        val b = song(2)
        val c = song(3)
        val base = listOf(a, b, a, c)
        val shuffled = listOf(a, c, b, a)

        harness.run {
            queue.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
            queue.setQueue(base, shuffled, 1)
        }

        val items = queue.queueStateFlow.value.items
        items.map { it.song } shouldBe shuffled
        items.map { it.uid }.toSet().size shouldBe 4
        queue.queueStateFlow.value.currentItem?.song shouldBe c
        queue.getQueue(QueueManager.ShuffleMode.Off).map { it.song } shouldBe base
    }

    @Test
    fun `RS-08 a tag edit renames queued songs without reloading or moving playback`() {
        val first = song(1)
        loadPaused(listOf(first, song(2)), positionMs = 1_200)
        val states = harness.record(playback.playbackStateFlow)
        val uid = queue.queueStateFlow.value.currentItem!!.uid

        playback.updateQueueSongs(listOf(first.copy(name = "Renamed")))
        harness.idle()

        queue.queueStateFlow.value.currentItem?.song?.name shouldBe "Renamed"
        queue.queueStateFlow.value.currentItem?.uid shouldBe uid
        states shouldNotContain PlaybackState.Loading
        playback.getProgress() shouldBe 1_200
        playback.positionAnchorFlow.value.positionMs shouldBe 1_200
    }

    @Test
    fun `RS-09 a song whose file can't be read is reported, and playback stops`() {
        val unreadable = unreadableSong(1)
        val failures = harness.record(playback.playbackFailureFlow)

        harness.run { playback.addToQueue(listOf(unreadable)) }
        harness.runUntil { failures.isNotEmpty() }
        harness.idle()

        failures shouldBe listOf(unreadable)
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-10 a song that can't be loaded is skipped for the next one that can`() {
        val playable = song(2)
        harness.run { playback.addToQueue(listOf(unresolvableSong(1), playable)) }

        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        queue.queueStateFlow.value.currentItem?.song shouldBe playable
    }

    @Test
    fun `RS-10 when no song can be loaded, playback stops on the last one tried`() {
        val songs = listOf(unresolvableSong(1), unresolvableSong(2))
        val states = harness.record(playback.playbackStateFlow)
        var result: Result<Boolean>? = null
        harness.run { queue.setQueue(songs) }

        playback.load { result = it }
        harness.runUntil { result != null }
        harness.idle()

        result!!.isFailure shouldBe true
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[1]
        states shouldContain PlaybackState.Loading
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-11 resuming from a saved position near the song's end starts the song again`() {
        val ended = harness.record(playback.trackEndedFlow)
        harness.run { queue.setQueue(listOf(song(1))) }
        harness.playbackPreferenceManager.playbackPosition = TONE_2S_MS - 100

        playback.play()
        harness.runUntil { ended.isNotEmpty() }

        harness.audioOutput().size.shouldBeBetween((TONE_2S_MS - 50) * BYTES_PER_MS, (TONE_2S_MS + 50) * BYTES_PER_MS)
    }

    @Test
    fun `RS-11 resuming from a saved position mid-song plays on from it`() {
        val ended = harness.record(playback.trackEndedFlow)
        harness.run { queue.setQueue(listOf(song(1))) }
        harness.playbackPreferenceManager.playbackPosition = 1_000

        playback.play()
        harness.runUntil { ended.isNotEmpty() }

        harness.audioOutput().size.shouldBeBetween((TONE_2S_MS - 1_000 - 50) * BYTES_PER_MS, (TONE_2S_MS - 1_000 + 50) * BYTES_PER_MS)
    }

    @Test
    fun `RS-12 repeat one set before anything plays repeats the song`() {
        val first = song(1)
        val ended = harness.record(playback.trackEndedFlow)
        queue.setRepeatMode(QueueManager.RepeatMode.One)

        harness.run { playback.addToQueue(listOf(first, song(2))) }
        harness.runUntil { ended.size >= 2 }

        ended.take(2) shouldBe listOf(first, first)
        queue.queueStateFlow.value.currentItem?.song shouldBe first
    }

    @Test
    fun `RS-13 a speed set before anything has loaded is reported`() {
        playback.setPlaybackSpeed(1.5f)

        playback.getPlaybackSpeed() shouldBe 1.5f
        playback.positionAnchorFlow.value.speed shouldBe 1.5f
    }

    @Test
    fun `RS-14 every change to the queue publishes a new queue state`() {
        val songs = (1L..5L).map { song(it) }
        loadPaused(songs)
        val states = harness.record(queue.queueStateFlow)

        fun publishes(change: () -> Unit) {
            val before = states.size
            val contentVersion = queue.queueStateFlow.value.contentVersion
            change()
            harness.idle()
            states.size shouldNotBe before
            queue.queueStateFlow.value.contentVersion shouldNotBe contentVersion
        }

        publishes { playback.moveQueueItem(3, 4) }
        publishes { playback.removeQueueItem(queue.queueStateFlow.value.items[4]) }
        publishes { harness.run { playback.addToQueue(listOf(song(6))) } }
        publishes { harness.run { playback.playNext(listOf(song(7))) } }
        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(1L, 7L, 2L, 3L, 5L, 6L)
    }

    @Test
    fun `RS-22 removing the current last song doesn't report it as played through`() {
        val first = song(1)
        val last = song(2)
        startPlaying(listOf(first, last))
        playback.skipTo(1)
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing && playback.getProgress()!! > 0 }
        val ended = harness.record(playback.trackEndedFlow)

        queue.remove(listOf(queue.queueStateFlow.value.currentItem!!))
        harness.idle()

        ended.shouldBeEmpty()
        queue.queueStateFlow.value.items.map { it.song } shouldBe listOf(first)
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-23 a song whose file can't be read is reported and skipped for the next one`() {
        val unreadable = unreadableSong(1)
        val playable = song(2)
        val failures = harness.record(playback.playbackFailureFlow)

        harness.run { playback.addToQueue(listOf(unreadable, playable)) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        queue.queueStateFlow.value.currentItem?.song shouldBe playable
        failures shouldBe listOf(unreadable)
    }

    @Test
    fun `RS-23 a song that can't be read is skipped when playback reaches it`() {
        val first = song(1, file = TONE_1S)
        val unreadable = unreadableSong(2)
        val third = song(3, file = TONE_1S)
        val failures = harness.record(playback.playbackFailureFlow)
        val ended = harness.record(playback.trackEndedFlow)

        harness.run { playback.addToQueue(listOf(first, unreadable, third)) }
        harness.runUntil { ended.size == 2 }

        ended shouldBe listOf(first, third)
        failures shouldBe listOf(unreadable)
    }

    @Test
    fun `RS-23 up to 15 songs in a row are tried before playback stops`() {
        val fourteenFailures = (1L..14L).map { unreadableSong(it) } + song(15)
        harness.run { playback.addToQueue(fourteenFailures) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        queue.queueStateFlow.value.currentItem?.song shouldBe fourteenFailures.last()
    }

    @Test
    fun `RS-23 playback stops on the 15th song that can't be loaded`() {
        val songs = (1L..15L).map { unreadableSong(it) } + song(16)
        val failures = harness.record(playback.playbackFailureFlow)
        var result: Result<Boolean>? = null
        harness.run { queue.setQueue(songs) }

        playback.load { result = it }
        harness.runUntil { result != null }
        harness.idle()

        result!!.isFailure shouldBe true
        failures shouldBe songs.take(15)
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[14]
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-24 a saved position only resumes the song it was saved for`() {
        val ended = harness.record(playback.trackEndedFlow)
        harness.run { queue.setQueue(listOf(song(1), song(2))) }
        harness.playbackPreferenceManager.playbackPosition = 1_000

        queue.setCurrentItem(queue.queueStateFlow.value.items[1])
        harness.idle()
        playback.play()
        harness.runUntil { ended.isNotEmpty() }

        ended.first() shouldBe song(2)
        harness.audioOutput().size.shouldBeBetween((TONE_2S_MS - 50) * BYTES_PER_MS, (TONE_2S_MS + 50) * BYTES_PER_MS)
    }

    @Test
    fun `RS-25 a tag edit with shuffle on keeps each song at its shuffled position`() {
        val songs = (1L..5L).map { song(it) }
        val shuffled = listOf(songs[0], songs[3], songs[1], songs[4], songs[2])
        harness.run {
            queue.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
            queue.setQueue(songs, shuffled, 0)
        }
        val before = queue.queueStateFlow.value.items.map { it.uid }

        playback.updateQueueSongs(listOf(songs[1].copy(name = "Renamed"), songs[3].copy(path = resourceUri(TONE_1S))))
        harness.idle()

        val items = queue.queueStateFlow.value.items
        items.map { it.uid } shouldBe before
        items.map { it.song.id } shouldBe shuffled.map { it.id }
        items[2].song.name shouldBe "Renamed"
        items[1].song.path shouldBe resourceUri(TONE_1S)
        queue.getQueue(QueueManager.ShuffleMode.Off).map { it.song.id } shouldBe songs.map { it.id }
    }

    @Test
    fun `RS-26 setting the same queue again with refreshed songs shows them without reloading`() {
        val first = song(1)
        val second = song(2)
        loadPaused(listOf(first, second), positionMs = 1_200)
        val states = harness.record(playback.playbackStateFlow)
        val uids = queue.queueStateFlow.value.items.map { it.uid }

        harness.run { queue.setQueue(listOf(first.copy(name = "Renamed"), second.copy(name = "Also renamed"))) }
        harness.idle()

        val items = queue.queueStateFlow.value.items
        items.map { it.song.name } shouldBe listOf("Renamed", "Also renamed")
        items.map { it.uid } shouldBe uids
        states shouldNotContain PlaybackState.Loading
        playback.getProgress() shouldBe 1_200
    }

    @Test
    fun `RS-29 playback and queue calls made off the main thread run on it`() {
        startPlaying(listOf(song(1), song(2), song(3)))
        val errors = mutableListOf<Throwable>()
        var progress: Int? = null

        offMainThread(errors) {
            progress = playback.getProgress()
            playback.getDuration()
            playback.pause()
            queue.remove(listOf(queue.queueStateFlow.value.items[2]))
        }
        harness.idle()

        errors.shouldBeEmpty()
        progress.shouldNotBeNull()
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(1L, 2L)
    }

    private fun offMainThread(
        errors: MutableList<Throwable>,
        block: () -> Unit
    ) {
        val thread = Thread { runCatching(block).onFailure { errors += it } }
        thread.start()
        thread.join()
    }

    /** Sets [songs] as the queue and plays it from the first, once it's playing. */
    private fun startPlaying(songs: List<Song>) {
        harness.run { playback.addToQueue(songs) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
    }

    /** Sets [songs] as the queue and loads the first, paused at [positionMs], as a restore does. */
    private fun loadPaused(
        songs: List<Song>,
        positionMs: Int = 0
    ) {
        harness.run { queue.setQueue(songs) }
        var result: Result<Boolean>? = null
        playback.load(positionMs) { result = it }
        harness.runUntil {
            result != null && playback.getDuration() == songs.first().duration && playback.playbackStateFlow.value == PlaybackState.Paused
        }
    }
}
