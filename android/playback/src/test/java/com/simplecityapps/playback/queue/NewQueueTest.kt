package com.simplecityapps.playback.queue

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * A queue built off the main thread with [QueueOperations.buildQueue] and set with
 * [QueueOperations.setQueueIfContentVersion], as the app's restore does: the same queue [QueueOperations.setQueue] sets.
 */
@RunWith(RobolectricTestRunner::class)
class NewQueueTest {
    private val builds = BuildDispatcher()

    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).build()

    private val queue =
        QueueFacade(
            player,
            PlaybackSettings(SettingsStore(FakeSharedPreferences())),
            SongUriResolver(MediaResolver { song -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false) }),
            buildContext = builds
        )

    @After
    fun tearDown() {
        player.release()
        builds.close()
    }

    private fun restore(
        songs: List<Song>,
        shuffleSongs: List<Song>?,
        position: Int,
        shuffleMode: ShuffleMode = ShuffleMode.Off
    ): Long? = runBlocking {
        val newQueue = queue.buildQueue(songs, shuffleSongs, position)
        queue.setQueueIfContentVersion(queue.queueStateFlow.value.contentVersion, newQueue, shuffleMode)
    }

    @Test
    fun `a queue is built off the main thread, and set as it was saved`() {
        val songs = (1L..5L).map { song(it) }

        restore(songs, shuffleSongs = songs.reversed(), position = 3).shouldNotBe(null)

        builds.threads.size shouldBe 1
        builds.threads.single() shouldNotBe Thread.currentThread()
        val state = queue.queueStateFlow.value
        state.items.map { it.song } shouldBe songs
        state.currentItem?.song shouldBe songs[3]
        state.currentPosition shouldBe 3
        queue.getQueue(ShuffleMode.On).map { it.song } shouldBe songs.reversed()
    }

    @Test
    fun `with shuffle on, the position is in the saved shuffled order, a song held twice included`() {
        val a = song(1)
        val b = song(2)
        val c = song(3)
        runBlocking { queue.setShuffleMode(ShuffleMode.On, reshuffle = false) }
        queue.setRepeatMode(RepeatMode.All)

        restore(listOf(a, b, a, c), shuffleSongs = listOf(a, c, b, a), position = 3, shuffleMode = ShuffleMode.On)

        val state = queue.queueStateFlow.value
        state.items.map { it.song } shouldBe listOf(a, c, b, a)
        state.items.map { it.uid }.toSet().size shouldBe 4
        state.currentPosition shouldBe 3
        // The second copy of a, not the first.
        player.currentMediaItemIndex shouldBe 2
        queue.getQueue(ShuffleMode.Off).map { it.song } shouldBe listOf(a, b, a, c)
        queue.shuffleModeFlow.value shouldBe ShuffleMode.On
        queue.repeatModeFlow.value shouldBe RepeatMode.All
    }

    @Test
    fun `with shuffle on, a saved current song no longer queued starts at the start of the shuffled order`() {
        val a = song(1)
        val b = song(2)
        val c = song(3)
        runBlocking { queue.setShuffleMode(ShuffleMode.On, reshuffle = false) }

        restore(listOf(a, b, c), shuffleSongs = listOf(c, song(9), a, b), position = 1, shuffleMode = ShuffleMode.On)

        queue.queueStateFlow.value.items.map { it.song } shouldBe listOf(c, a, b)
        queue.queueStateFlow.value.currentItem?.song shouldBe c
    }

    @Test
    fun `the shuffle mode is set with the queue, so the position is in its order whatever the player's mode was`() {
        val songs = (1L..4L).map { song(it) }
        val shuffleSongs = listOf(songs[2], songs[0], songs[3], songs[1])

        // Shuffle is still off: nothing has set the saved mode yet.
        restore(songs, shuffleSongs, position = 0, shuffleMode = ShuffleMode.On)

        queue.shuffleModeFlow.value shouldBe ShuffleMode.On
        queue.queueStateFlow.value.items.map { it.song } shouldBe shuffleSongs
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[2]
    }

    @Test
    fun `a queue set with shuffle off is in the saved order, though the player's shuffle was on`() {
        val songs = (1L..4L).map { song(it) }
        runBlocking { queue.setShuffleMode(ShuffleMode.On, reshuffle = false) }

        restore(songs, shuffleSongs = songs.reversed(), position = 1, shuffleMode = ShuffleMode.Off)

        queue.shuffleModeFlow.value shouldBe ShuffleMode.Off
        queue.queueStateFlow.value.items.map { it.song } shouldBe songs
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[1]
    }

    @Test
    fun `a queue changed since the version it was built for is left alone`() {
        val newQueue = runBlocking { queue.buildQueue(listOf(song(1), song(2)), null, 1) }
        val version = queue.queueStateFlow.value.contentVersion
        runBlocking { queue.setQueue(listOf(song(3))) }

        queue.setQueueIfContentVersion(version, newQueue, ShuffleMode.Off).shouldBeNull()

        queue.queueStateFlow.value.items.map { it.song.id } shouldBe listOf(3L)
    }

    @Test
    fun `a queue is set only on the main thread`() {
        val newQueue = runBlocking { queue.buildQueue(listOf(song(1)), null, 0) }
        val version = queue.queueStateFlow.value.contentVersion
        var error: Throwable? = null

        Thread { error = runCatching { queue.setQueueIfContentVersion(version, newQueue, ShuffleMode.Off) }.exceptionOrNull() }
            .apply { start() }
            .join()

        shouldThrow<IllegalStateException> { error?.let { throw it } }
        queue.queueStateFlow.value.items.size shouldBe 0
    }

    @Test
    fun `a queue another QueueOperations built is refused`() {
        val foreign = object : NewQueue {
            override val songs = listOf(song(1))
            override val shuffleSongs: List<Song>? = null
            override val position = 0
        }

        shouldThrow<IllegalArgumentException> { queue.setQueueIfContentVersion(queue.queueStateFlow.value.contentVersion, foreign, ShuffleMode.Off) }
        queue.queueStateFlow.value.items.size shouldBe 0
    }

    /** Runs builds on a thread of its own, recording it. */
    private class BuildDispatcher : CoroutineDispatcher() {
        private val executor = Executors.newSingleThreadExecutor()

        val threads = mutableListOf<Thread>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable
        ) {
            executor.execute {
                synchronized(threads) { threads += Thread.currentThread() }
                block.run()
            }
        }

        fun close() = executor.shutdown()
    }
}
