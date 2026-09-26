package com.simplecityapps.playback.persistence

import android.content.SharedPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.chromecast.FakeSongRepository
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.longSong
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.query.SongQuery
import com.squareup.moshi.Moshi
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [QueueStore] on the real playback stack: what it saves as the queue and playback change, and what it restores when
 * the app starts again. Tests drive playback through the harness's operations and read the saved state back through
 * [PlaybackPreferenceManager], as the app does.
 */
@RunWith(RobolectricTestRunner::class)
class QueueStoreTest {
    private val harnesses = mutableListOf<PlaybackHarness>()

    private val preferences = WriteRecordingPreferences()

    /** The library the saved queue is restored from: long songs, so a saved position is well inside each. */
    private val library = (1L..4L).map { longSong(it) }

    @After
    fun tearDown() {
        harnesses.forEach(PlaybackHarness::release)
    }

    private fun harness(
        songRepository: SongRepository = FakeSongRepository(library),
        sharedPreferences: SharedPreferences = preferences,
        exceptionHandler: CoroutineExceptionHandler? = null
    ) = PlaybackHarness(sharedPreferences = sharedPreferences, songRepository = songRepository, exceptionHandler = exceptionHandler).also { harnesses += it }

    /** What's saved, read as the app reads it. */
    private val saved = PlaybackPreferenceManager(preferences, Moshi.Builder().build())

    private fun PlaybackHarness.setQueue(
        songs: List<Song>,
        position: Int = 0
    ) {
        run { queueOperations.setQueue(songs, position = position) }
        settle()
    }

    /**
     * Turns the main looper until the player has handled what it was asked and reported it. [PlaybackHarness.idle] can
     * finish while the player's thread still holds an event back, which would leave it unsaved.
     */
    private fun PlaybackHarness.settle() {
        idle()
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(appPlayer as ExoPlayer)
    }

    private fun PlaybackHarness.loadPaused(positionMs: Int) {
        var result: Result<Boolean>? = null
        playbackOperations.load(positionMs) { result = it }
        runUntil { result != null && playbackOperations.playbackStateFlow.value == PlaybackState.Paused }
    }

    private fun PlaybackHarness.currentIds() = queueOperations.queueStateFlow.value.items.map { item -> item.song.id }

    // Saving the queue

    @Test
    fun `a queue change saves the queue, its position and the song there`() {
        val harness = harness()

        harness.setQueue(library.take(3), position = 1)

        saved.queueIds shouldBe "1,2,3"
        saved.shuffleQueueIds!!.split(",") shouldContainExactlyInAnyOrder listOf("1", "2", "3")
        saved.queuePosition shouldBe 1
        saved.restoreQueuePositionFromStart shouldBe false
        saved.nowPlaying?.songId shouldBe 2L
    }

    @Test
    fun `the empty queue the app starts with saves nothing over the saved one`() {
        saved(queueIds = "7,8", queuePosition = 1, playbackPosition = 30_000)
        val prefs = preferences.snapshot()

        val harness = harness()
        harness.settle()

        preferences.snapshot() shouldBe prefs
    }

    @Test
    fun `a move to another song saves the position in the queue, but not the queue again`() {
        val harness = harness()
        harness.setQueue(library.take(3))
        preferences.writes.clear()

        harness.queueOperations.skipTo(2)
        harness.settle()

        saved.queuePosition shouldBe 2
        saved.nowPlaying?.songId shouldBe 3L
        preferences.writes.keys shouldNotContainAny listOf("queue_ids", "shuffle_queue_ids")
    }

    @Test
    fun `a queue made in several player calls is saved once, as it's left`() {
        val harness = harness()
        harness.setQueue(library.take(2))
        preferences.writes.clear()

        // Setting a queue with shuffle on takes a playlist change and a shuffle order change, and moves the current item.
        harness.run { harness.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false) }
        harness.run { harness.queueOperations.setQueue(library, library.reversed(), 0) }
        harness.settle()

        preferences.writes["queue_ids"] shouldBe 1
        preferences.writes["shuffle_queue_ids"] shouldBe 1
        saved.queueIds shouldBe "1,2,3,4"
        saved.shuffleQueueIds shouldBe "4,3,2,1"
        saved.queuePosition shouldBe 0
        saved.nowPlaying?.songId shouldBe 4L
    }

    @Test
    fun `shuffle and repeat changes are saved`() {
        val harness = harness()

        harness.run { harness.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false) }
        harness.queueOperations.setRepeatMode(RepeatMode.One)
        harness.settle()

        saved.shuffleMode shouldBe ShuffleMode.On
        saved.repeatMode shouldBe RepeatMode.One
    }

    @Test
    fun `with shuffle on, the position is saved in the shuffled order`() {
        val harness = harness()
        harness.run { harness.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false) }

        harness.run { harness.queueOperations.setQueue(library.take(3), listOf(library[2], library[0], library[1]), 2) }
        harness.settle()

        saved.shuffleQueueIds shouldBe "3,1,2"
        saved.queuePosition shouldBe 2
        saved.nowPlaying?.songId shouldBe 2L
    }

    @Test
    fun `songs not in the library are left out of the saved queue, and the position is found among the rest`() {
        val harness = harness()

        harness.setQueue(listOf(library[0], opened(5), library[1], library[2]), position = 2)

        saved.queueIds shouldBe "1,2,3"
        saved.queuePosition shouldBe 1
        saved.restoreQueuePositionFromStart shouldBe false
    }

    @Test
    fun `while an opened file plays, the saved position is the library song after it, from the start`() {
        val harness = harness()

        harness.setQueue(listOf(library[0], opened(5), library[1]), position = 1)
        saved.queuePosition shouldBe 1
        saved.restoreQueuePositionFromStart shouldBe true
        saved.nowPlaying?.songId shouldBe 2L

        // With no library song after it, the one before.
        harness.setQueue(listOf(library[0], opened(5)), position = 1)
        saved.queueIds shouldBe "1"
        saved.queuePosition shouldBe 0
        saved.restoreQueuePositionFromStart shouldBe true

        // On its own, there's nothing to save.
        harness.setQueue(listOf(opened(5)))
        saved.queueIds shouldBe null
        saved.queuePosition shouldBe null
        saved.nowPlaying shouldBe null
    }

    @Test
    fun `the saved song is read back with where it resumes from, or its start while an opened file plays`() {
        val harness = harness()
        harness.setQueue(library.take(2))
        harness.loadPaused(30_000)

        saved.nowPlaying?.songId shouldBe 1L
        saved.nowPlaying?.positionMs shouldBe 30_000

        harness.setQueue(listOf(opened(5), library[1]))
        saved.nowPlaying?.songId shouldBe 2L
        saved.nowPlaying?.positionMs shouldBe 0
    }

    @Test
    fun `an emptied queue clears the saved queue, its song and the position`() {
        val harness = harness()
        harness.setQueue(library.take(2))
        harness.loadPaused(30_000)

        harness.queueOperations.clear()
        harness.settle()

        saved.queueIds shouldBe null
        saved.queuePosition shouldBe null
        saved.nowPlaying shouldBe null
        saved.playbackPosition shouldBe null
    }

    // Saving the position

    @Test
    fun `the position is saved while playing, and where playback pauses`() {
        val harness = harness()
        harness.setQueue(library.take(1))
        harness.loadPaused(0)
        saved.playbackPosition shouldBe 0

        harness.playbackOperations.play()
        harness.runUntil { (saved.playbackPosition ?: 0) > 0 }
        harness.playbackOperations.playbackStateFlow.value shouldBe PlaybackState.Playing

        harness.playbackOperations.pause()
        harness.settle()
        saved.playbackPosition shouldBe harness.playbackOperations.getProgress()
    }

    @Test
    fun `a seek saves the new position straight away, backwards as well as forwards`() {
        val harness = harness()
        harness.setQueue(library.take(1))
        harness.loadPaused(30_000)
        saved.playbackPosition shouldBe 30_000

        harness.playbackOperations.seekTo(10_000)
        harness.settle()

        saved.playbackPosition shouldBe 10_000
    }

    @Test
    fun `a move to another song clears the saved position, so it resumes from its own start`() {
        val harness = harness()
        harness.setQueue(library.take(2))
        harness.loadPaused(30_000)

        harness.queueOperations.setCurrentItem(harness.queueOperations.queueStateFlow.value.items[1])
        harness.settle()

        saved.playbackPosition shouldBe null
    }

    @Test
    fun `the first song of a queue set on an empty one keeps the saved position it was restored at`() {
        saved(playbackPosition = 30_000)
        val harness = harness()

        harness.setQueue(library.take(2))

        saved.playbackPosition shouldBe 30_000
    }

    @Test
    fun `playing on to the next song keeps saving the position, where a skip would clear it`() {
        val harness = harness()
        harness.setQueue(listOf(song(1, file = TONE_1S), library[1]))
        harness.loadPaused(500)
        preferences.positions.clear()

        harness.playbackOperations.play()
        harness.runUntil { harness.queueOperations.queueStateFlow.value.currentPosition == 1 }
        harness.playbackOperations.pause()
        harness.settle()

        preferences.positions shouldNotContain -1
        saved.playbackPosition shouldBe harness.playbackOperations.getProgress()
    }

    @Test
    fun `with nothing saved, a podcast resumes a little before where it was left, and music from its start`() {
        val store = harness().queueStore
        val podcast = testSong(3, path = "/podcasts/episode.mp3").copy(playbackPosition = 60_000)
        val justStarted = testSong(4, path = "/books/chapter.m4b").copy(playbackPosition = 2_000)

        store.resumePosition(podcast) shouldBe 55_000
        store.resumePosition(justStarted) shouldBe 0
        store.resumePosition(testSong(5).copy(playbackPosition = 60_000)) shouldBe 0

        saved.playbackPosition = 12_000
        store.resumePosition(podcast) shouldBe 12_000
    }

    // Restoring

    @Test
    fun `the saved queue is restored, loaded paused at the saved position`() {
        saved(queueIds = "1,2,3", queuePosition = 1, playbackPosition = 30_000, repeatMode = RepeatMode.All)
        val harness = harness()

        harness.restore()

        harness.currentIds() shouldBe listOf(1L, 2L, 3L)
        harness.queueOperations.queueStateFlow.value.currentPosition shouldBe 1
        harness.queueOperations.repeatModeFlow.value shouldBe RepeatMode.All
        harness.runUntil { harness.playbackOperations.playbackStateFlow.value == PlaybackState.Paused }
        harness.playbackOperations.getProgress() shouldBe 30_000
    }

    @Test
    fun `a restore drops songs no longer in the library and keeps the position on the saved song`() {
        saved(queueIds = "1,2,3,1,4", queuePosition = 4, playbackPosition = 30_000)
        val harness = harness(FakeSongRepository(listOf(library[0], library[2], library[3])))

        harness.restore()

        harness.currentIds() shouldBe listOf(1L, 3L, 1L, 4L)
        harness.queueOperations.queueStateFlow.value.currentPosition shouldBe 3
        harness.playbackOperations.getProgress() shouldBe 30_000
    }

    @Test
    fun `a restore that brings back every saved song doesn't save the queue again, and a change after it is saved`() {
        saved(queueIds = "1,2,3", shuffleQueueIds = "3,1,2", queuePosition = 1, playbackPosition = 30_000)
        preferences.writes.clear()
        val harness = harness()

        harness.restore()

        preferences.writes.keys shouldNotContainAny listOf("queue_ids", "shuffle_queue_ids")
        saved.playbackPosition shouldBe 30_000
        saved.nowPlaying?.songId shouldBe 2L

        harness.run { harness.queueOperations.addToQueue(listOf(library[3])) }
        harness.settle()
        saved.queueIds shouldBe "1,2,3,4"
    }

    @Test
    fun `a restore that drops songs saves the queue that's left`() {
        saved(queueIds = "1,2,3", shuffleQueueIds = "1,2,3", queuePosition = 2)
        val harness = harness(FakeSongRepository(listOf(library[0], library[2])))

        harness.restore()

        saved.queueIds shouldBe "1,3"
        saved.shuffleQueueIds shouldBe "1,3"
        saved.queuePosition shouldBe 1
    }

    @Test
    fun `a restore sets the saved shuffle mode with the queue, the position in the saved shuffled order`() {
        saved(queueIds = "1,2,3", shuffleQueueIds = "3,1,2", queuePosition = 1, shuffleMode = ShuffleMode.On)
        val harness = harness()

        harness.restore()

        harness.queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.On
        harness.currentIds() shouldBe listOf(3L, 1L, 2L)
        harness.queueOperations.queueStateFlow.value.currentItem?.song?.id shouldBe 1L
        harness.queueOperations.getQueue(ShuffleMode.Off).map { item -> item.song.id } shouldBe listOf(1L, 2L, 3L)
    }

    @Test
    fun `a restore whose saved song is gone starts the next one from the beginning`() {
        saved(queueIds = "1,2,3", queuePosition = 1, playbackPosition = 30_000)
        val harness = harness(FakeSongRepository(listOf(library[0], library[2])))

        harness.restore()

        harness.queueOperations.queueStateFlow.value.currentItem?.song?.id shouldBe 3L
        harness.playbackOperations.getProgress() shouldBe 0
        saved.playbackPosition shouldBe 0
    }

    @Test
    fun `a restore saved while an opened file played starts the saved song from the beginning`() {
        saved(queueIds = "1,2,3", queuePosition = 1, playbackPosition = 30_000, fromStart = true)
        val harness = harness()

        harness.restore()

        harness.queueOperations.queueStateFlow.value.currentItem?.song?.id shouldBe 2L
        harness.playbackOperations.getProgress() shouldBe 0
    }

    @Test
    fun `a restore that brings nothing back clears the saved song`() {
        saved(queueIds = "1,2,3", queuePosition = 1)
        saved.nowPlaying = NowPlayingSnapshot.of(library[1])
        val harness = harness(FakeSongRepository(emptyList()))

        harness.restore()

        harness.currentIds() shouldBe emptyList()
        saved.nowPlaying shouldBe null
    }

    @Test
    fun `a restore that throws still marks the queue restored, so requests waiting on it go ahead`() {
        saved(queueIds = "1,2,3", queuePosition = 0)
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val failing = object : SongRepository by FakeSongRepository(library) {
            override fun getSongs(query: SongQuery): Flow<List<Song>?> = throw IllegalStateException("database unavailable")
        }
        val harness = harness(failing, exceptionHandler = CoroutineExceptionHandler { _, error -> failures += error })

        harness.restore()

        harness.runUntil { failures.isNotEmpty() }
        failures.single().message shouldBe "database unavailable"
    }

    @Test
    fun `a play request made while the restore is still reading the saved queue wins, and the restore is dropped`() {
        saved(queueIds = "1,2,3", queuePosition = 1, playbackPosition = 30_000)
        val read = CompletableDeferred<Unit>()
        val harness = harness(SlowSongRepository(library, read))
        harness.queueStore.restore { positionMs -> harness.playbackOperations.load(positionMs, skipUnloadable = false) {} }

        // A request that gave up waiting for the restore sets its own queue, and plays it.
        harness.run { harness.playbackOperations.addToQueue(listOf(library[3])) }
        harness.runUntil { harness.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }
        read.complete(Unit)
        harness.runUntil { harness.queueOperations.hasRestoredQueue }

        harness.currentIds() shouldBe listOf(4L)
        harness.playbackOperations.playbackStateFlow.value shouldBe PlaybackState.Playing
        saved.queueIds shouldBe "4"
        saved.nowPlaying?.songId shouldBe 4L
    }

    @Test
    fun `a restore with nothing to bring back leaves a queue set while it read, and that queue's saved song, alone`() {
        // The content version the restore started from has moved on, though there's no saved queue to set.
        saved(queueIds = "1,2,3", queuePosition = 1)
        val read = CompletableDeferred<Unit>()
        val harness = harness(SlowSongRepository(emptyList(), read))
        harness.queueStore.restore { positionMs -> harness.playbackOperations.load(positionMs, skipUnloadable = false) {} }

        harness.setQueue(listOf(library[3]))
        read.complete(Unit)
        harness.runUntil { harness.queueOperations.hasRestoredQueue }

        harness.currentIds() shouldBe listOf(4L)
        saved.nowPlaying?.songId shouldBe 4L
        saved.queueIds shouldBe "4"
    }

    @Test
    fun `a restore reads the saved queue off the main thread, then sets and loads it before marking it restored`() {
        saved(queueIds = "1,2,3", queuePosition = 1, playbackPosition = 30_000)
        val repository = SlowSongRepository(library, CompletableDeferred(Unit))
        val harness = harness(repository)
        var atRestored: Pair<List<Long>, Long>? = null
        harness.launch {
            harness.queueOperations.queueStateFlow.collect { state ->
                if (state.isRestored && atRestored == null) atRestored = harness.currentIds() to harness.appPlayer.currentPosition
            }
        }

        harness.restore()

        repository.readThreads.single() shouldNotBe Thread.currentThread()
        atRestored shouldBe (listOf(1L, 2L, 3L) to 30_000L)
    }

    @Test
    fun `saved preferences in the current format restore identically`() {
        // As the app before QueueStore wrote them, key for key.
        preferences.edit()
            .putString("queue_ids", "1,2,3,4")
            .putString("shuffle_queue_ids", "4,2,1,3")
            .putInt("queue_position", 2)
            .putBoolean("restore_queue_position_from_start", false)
            .putInt("playback_position", 45_000)
            .putInt("shuffle_mode", ShuffleMode.On.ordinal)
            .putInt("repeat_mode", RepeatMode.All.ordinal)
            .apply()
        val queueKeys = listOf("queue_ids", "shuffle_queue_ids", "queue_position", "restore_queue_position_from_start", "playback_position", "shuffle_mode", "repeat_mode")
        val before = preferences.snapshot().filterKeys { it in queueKeys }
        val harness = harness()

        harness.restore()

        harness.queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.On
        harness.queueOperations.repeatModeFlow.value shouldBe RepeatMode.All
        harness.currentIds() shouldBe listOf(4L, 2L, 1L, 3L)
        harness.queueOperations.getQueue(ShuffleMode.Off).map { item -> item.song.id } shouldBe listOf(1L, 2L, 3L, 4L)
        harness.queueOperations.queueStateFlow.value.currentItem?.song?.id shouldBe 1L
        harness.playbackOperations.getProgress() shouldBe 45_000
        preferences.snapshot().filterKeys { it in queueKeys } shouldBe before
        saved.nowPlaying?.songId shouldBe 1L
    }

    @Test
    fun `what's saved as the app runs is restored when it starts again`() {
        val first = harness()
        first.run { first.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = false) }
        first.run { first.queueOperations.setQueue(library, listOf(library[2], library[0], library[3], library[1]), 1) }
        first.settle()
        first.queueOperations.setRepeatMode(RepeatMode.One)
        first.loadPaused(20_000)
        first.playbackOperations.seekTo(15_000)
        first.settle()
        val presented = first.currentIds()

        val restarted = harness()
        restarted.restore()

        restarted.queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.On
        restarted.queueOperations.repeatModeFlow.value shouldBe RepeatMode.One
        restarted.currentIds() shouldBe presented
        restarted.queueOperations.queueStateFlow.value.currentItem?.song?.id shouldBe 1L
        restarted.playbackOperations.getProgress() shouldBe 15_000
    }

    // Helpers

    /** A song opened from another app: not in the library, so it has no id to save. */
    private fun opened(id: Long): Song = song(-id)

    private fun saved(
        queueIds: String? = null,
        shuffleQueueIds: String? = null,
        queuePosition: Int? = null,
        playbackPosition: Int? = null,
        fromStart: Boolean = false,
        shuffleMode: ShuffleMode = ShuffleMode.Off,
        repeatMode: RepeatMode = RepeatMode.Off
    ) {
        saved.queueIds = queueIds
        saved.shuffleQueueIds = shuffleQueueIds
        saved.queuePosition = queuePosition
        saved.playbackPosition = playbackPosition
        saved.restoreQueuePositionFromStart = fromStart
        saved.shuffleMode = shuffleMode
        saved.repeatMode = repeatMode
    }

    private infix fun Collection<String>.shouldNotContainAny(keys: List<String>) = filter { it in keys } shouldBe emptyList()

    /** Preferences that count the writes to each key. */
    private class WriteRecordingPreferences(
        private val delegate: SharedPreferences = FakeSharedPreferences()
    ) : SharedPreferences by delegate {
        val writes = mutableMapOf<String, Int>()

        /** Each playback position written, -1 for none. */
        val positions = mutableListOf<Int>()

        fun snapshot(): Map<String, Any?> = delegate.all.toMap()

        override fun edit(): SharedPreferences.Editor = RecordingEditor(delegate.edit())

        private inner class RecordingEditor(
            private val editor: SharedPreferences.Editor
        ) : SharedPreferences.Editor by editor {
            private fun record(key: String) = apply { writes[key] = (writes[key] ?: 0) + 1 }

            override fun putString(
                key: String,
                value: String?
            ) = record(key).also { editor.putString(key, value) }

            override fun putInt(
                key: String,
                value: Int
            ) = record(key).also {
                if (key == "playback_position") positions += value
                editor.putInt(key, value)
            }

            override fun putBoolean(
                key: String,
                value: Boolean
            ) = record(key).also { editor.putBoolean(key, value) }

            override fun putLong(
                key: String,
                value: Long
            ) = record(key).also { editor.putLong(key, value) }
        }
    }

    /** A library whose songs are read only once [read] completes, recording the thread each read ran on. */
    private class SlowSongRepository(
        private val songs: List<Song>,
        private val read: CompletableDeferred<Unit>
    ) : SongRepository by FakeSongRepository(songs) {
        val readThreads: MutableList<Thread> = Collections.synchronizedList(mutableListOf())

        override fun getSongs(query: SongQuery): Flow<List<Song>?> = flow {
            readThreads += Thread.currentThread()
            read.await()
            emit(songs.filter(query.predicate))
        }
    }
}
