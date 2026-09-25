package com.simplecityapps.playback.spec

import android.media.AudioManager
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.BYTES_PER_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.LONG_SONG_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_2S_MS
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_3S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.deleteFile
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.longSong
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
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
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
    fun `RS-04 pausing keeps audio focus, and another app asking for it while paused gets it`() {
        val audioManager = shadowOf(harness.audioManager)
        harness.run { playback.addToQueue(listOf(song(1), song(2))) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        audioManager.lastAudioFocusRequest.shouldNotBeNull()

        playback.pause()
        harness.idle()

        harness.audioFocus.abandons shouldBe 0
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS)

        audioManager.lastAbandonedAudioFocusRequest.shouldNotBeNull()
        harness.audioFocus.abandons shouldBe 1
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    @Test
    fun `RS-04 an interruption while paused doesn't start playback when it ends`() {
        harness.run { playback.addToQueue(listOf(song(1), song(2))) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        playback.pause()
        harness.idle()

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_GAIN)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
        harness.appPlayer.isPlaying shouldBe false
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
    fun `RS-56 a restored song that can't be loaded stays current, paused, until it's played`() {
        val unreadable = unreadableSong(1)
        val playable = song(2)
        val failures = harness.record(playback.playbackFailureFlow)
        var result: Result<Boolean>? = null
        harness.run { queue.setQueue(listOf(unreadable, playable)) }

        playback.load(skipUnloadable = false) { result = it }
        harness.runUntil { result != null }
        harness.idle()

        result!!.isFailure shouldBe true
        queue.queueStateFlow.value.currentItem?.song shouldBe unreadable
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        failures shouldBe listOf(unreadable)

        playback.play()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }

        queue.queueStateFlow.value.currentItem?.song shouldBe playable
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
        harness.appPlayer.playbackParameters.pitch shouldBe 1f
    }

    @Test
    fun `RS-13 a speed set is restored when the app starts again`() {
        playback.setPlaybackSpeed(1.25f)
        harness.release()

        val restarted = PlaybackHarness(sharedPreferences = harness.sharedPreferences)
        try {
            restarted.playbackOperations.getPlaybackSpeed() shouldBe 1.25f
            restarted.playbackOperations.positionAnchorFlow.value.speed shouldBe 1.25f
        } finally {
            restarted.release()
        }
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
    fun `RS-27 a saved shuffle order holding a song no longer queued restores the saved current song`() {
        val a = song(1)
        val b = song(2)
        val c = song(3)
        val gone = song(9)
        val shuffled = listOf(a, gone, c, b)

        listOf(2 to c, 3 to b).forEach { (position, current) ->
            harness.run {
                queue.setShuffleMode(QueueManager.ShuffleMode.On, reshuffle = false)
                queue.setQueue(listOf(a, b, c), shuffled, position).shouldBe(true)
            }

            queue.queueStateFlow.value.items.map { it.song } shouldBe listOf(a, c, b)
            queue.queueStateFlow.value.currentItem?.song shouldBe current
            harness.run { queue.setQueue(listOf(song(4))) }
        }
    }

    @Test
    fun `RS-28 clearing the queue while playing keeps the current song, in one change`() {
        startPlaying((1L..300L).map { song(it) })
        val changes = harness.playlistChanges

        playback.clearQueue()
        harness.idle()

        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(1L)
        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
        harness.playlistChanges - changes shouldBe 1
    }

    @Test
    fun `RS-28 removing a run of songs is one change`() {
        startPlaying((1L..300L).map { song(it) })
        val items = queue.queueStateFlow.value.items
        val changes = harness.playlistChanges

        queue.remove(items.subList(10, 100) + items.subList(200, 250))
        harness.idle()

        queue.queueStateFlow.value.items shouldBe items.take(10) + items.subList(100, 200) + items.drop(250)
        harness.playlistChanges - changes shouldBe 2
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

    @Test
    fun `RS-30 removing the playing song plays the next one`() {
        startPlaying(listOf(song(1), song(2), song(3)))

        playback.removeQueueItem(queue.queueStateFlow.value.currentItem!!)
        harness.idle()

        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(2L, 3L)
        queue.queueStateFlow.value.currentItem?.song?.id shouldBe 2L
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing && (playback.getProgress() ?: 0) > 0 }
    }

    @Test
    fun `RS-30 removing the paused song makes the next one current, still paused`() {
        loadPaused(listOf(song(1), song(2), song(3)), positionMs = 1_200)

        playback.removeQueueItem(queue.queueStateFlow.value.currentItem!!)
        harness.runUntil { playback.playbackStateFlow.value != PlaybackState.Loading }

        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(2L, 3L)
        queue.queueStateFlow.value.currentItem?.song?.id shouldBe 2L
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        playback.getProgress() shouldBe 0
    }

    @Test
    fun `RS-31 clearing the queue while paused empties it`() {
        loadPaused(listOf(song(1), song(2)))

        playback.clearQueue()
        harness.idle()

        queue.queueStateFlow.value.items.shouldBeEmpty()
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-32 songs played next with shuffle on come straight after the current song in both orders`() {
        val songs = (1L..5L).map { song(it) }
        val added = (6L..7L).map { song(it) }
        harness.run { playback.shuffle(songs) {} }
        val current = queue.queueStateFlow.value.currentItem!!
        val shuffled = queue.queueStateFlow.value.items
        val unshuffled = queue.getQueue(QueueManager.ShuffleMode.Off)

        harness.run { playback.playNext(added) }

        val position = shuffled.indexOf(current)
        queue.queueStateFlow.value.items.map { it.song } shouldBe
            shuffled.take(position + 1).map { it.song } + added + shuffled.drop(position + 1).map { it.song }
        val unshuffledPosition = unshuffled.indexOfFirst { it.uid == current.uid }
        queue.getQueue(QueueManager.ShuffleMode.Off).map { it.song } shouldBe
            unshuffled.take(unshuffledPosition + 1).map { it.song } + added + unshuffled.drop(unshuffledPosition + 1).map { it.song }
        queue.queueStateFlow.value.currentItem?.uid shouldBe current.uid
    }

    @Test
    fun `RS-33 previous within the first 2 seconds goes back a song`() {
        startPlaying(listOf(song(1), song(2, file = TONE_3S)))
        playback.skipToNext()
        harness.runUntil { queue.queueStateFlow.value.currentItem?.song?.id == 2L && playback.playbackStateFlow.value == PlaybackState.Playing }

        playback.skipToPrev()
        harness.idle()

        queue.queueStateFlow.value.currentItem?.song?.id shouldBe 1L
    }

    @Test
    fun `RS-33 previous after the first 2 seconds restarts the song`() {
        startPlaying(listOf(song(1), song(2, file = TONE_3S)))
        playback.skipToNext()
        harness.runUntil { queue.queueStateFlow.value.currentItem?.song?.id == 2L && (playback.getProgress() ?: 0) > 2_100 }

        playback.skipToPrev()
        harness.idle()

        queue.queueStateFlow.value.currentItem?.song?.id shouldBe 2L
        playback.getProgress()!!.shouldBeBetween(0, 100)
    }

    @Test
    fun `RS-34 a seek on a song reached by playing on shows it playing, not loading`() {
        startPlaying(listOf(song(1, file = TONE_1S), song(2, file = TONE_3S)))
        harness.runUntil { queue.queueStateFlow.value.currentItem?.song?.id == 2L && (playback.getProgress() ?: 0) > 0 }
        val states = harness.record(playback.playbackStateFlow)

        playback.seekTo(1_500)
        harness.runUntil { (playback.getProgress() ?: 0) > 1_600 }

        states shouldNotContain PlaybackState.Loading
        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
    }

    @Test
    fun `RS-34 a song reached by playing on that fails once playing stops playback`() {
        val failing = longSong(2)
        val failures = harness.record(playback.playbackFailureFlow)
        startPlaying(listOf(song(1, file = TONE_1S), failing, song(3)))
        harness.runUntil { queue.queueStateFlow.value.currentItem?.song == failing && (playback.getProgress() ?: 0) > 0 }

        deleteFile(failing)
        playback.seekTo(LONG_SONG_MS - 10_000)
        harness.runUntil { failures.isNotEmpty() }
        harness.idle()

        failures shouldBe listOf(failing)
        queue.queueStateFlow.value.currentItem?.song shouldBe failing
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-35 a song loaded paused shows its real length once it's ready`() {
        val tagged = song(1, durationMs = 5_000)
        harness.run { queue.setQueue(listOf(tagged)) }
        var result: Result<Boolean>? = null

        playback.load(0) { result = it }
        harness.runUntil { result != null }
        harness.idle()

        playback.progressFlow.value shouldBe PlaybackProgress(0, TONE_2S_MS)
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
    }

    @Test
    fun `RS-49 play-pause while a song is still loading paused plays it, and while it's loading to play pauses it`() {
        harness.run { queue.setQueue(listOf(song(1), song(2))) }

        // The saved queue's restore loads paused; the widget's play-pause lands before it's ready.
        playback.load(1_000) {}
        playback.playbackStateFlow.value shouldBe PlaybackState.Loading
        playback.togglePlayback()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        queue.queueStateFlow.value.currentItem?.song shouldBe song(1)

        // A skip loads to play: play-pause before it's ready pauses.
        playback.skipToNext()
        playback.playbackStateFlow.value shouldBe PlaybackState.Loading
        playback.togglePlayback()
        harness.idle()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Paused }
    }

    @Test
    fun `RS-36 songs added while a new queue is still being built join it, after it`() {
        val builds = HeldDispatcher()
        val harness = PlaybackHarness(buildContext = builds)
        try {
            harness.launch { harness.queueOperations.setQueue(listOf(song(1))) }
            builds.runAll()

            harness.launch { harness.queueOperations.setQueue((2L..4L).map { song(it) }) }
            harness.launch { harness.playbackOperations.addToQueue(listOf(song(5))) }
            harness.launch { harness.playbackOperations.playNext(listOf(song(6))) }
            builds.runAll()
            harness.idle()

            harness.queueOperations.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(2L, 6L, 3L, 4L, 5L)
        } finally {
            harness.release()
        }
    }

    /** Holds the builds dispatched to it until [runAll], which finishes the latest first. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable
        ) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeLast().run()
        }
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
