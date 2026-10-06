package com.simplecityapps.playback.queue

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [QueueFacade.skipToNext] and [QueueFacade.skipToPrevious] step through the player's timeline in its shuffle order,
 * under its repeat mode, and land where [QueueFacade.getNext] and [QueueFacade.getPrevious] said they would.
 */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer, whose timeline the skips step through.
@RunWith(RobolectricTestRunner::class)
class QueueSkipTest {
    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).build()

    private val queue =
        QueueFacade(
            player,
            PlaybackSettings(SettingsStore(InMemoryKeyValueStore())),
            SongUriResolver(MediaResolver { song -> ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false) }),
            buildContext = Dispatchers.Unconfined
        )

    private val songs = (1L..4L).map { song(it) }

    @After
    fun tearDown() {
        player.release()
    }

    /** Queues [songs], shuffled as their reverse when [shuffleMode] is on, at [position] in that order. */
    private fun queueAt(
        position: Int,
        repeatMode: RepeatMode = RepeatMode.Off,
        shuffleMode: ShuffleMode = ShuffleMode.Off
    ) = runBlocking {
        val newQueue = queue.buildQueue(songs, songs.reversed(), position)
        queue.setQueueIfContentVersion(queue.queueStateFlow.value.contentVersion, newQueue, shuffleMode)
        queue.setRepeatMode(repeatMode)
    }

    private fun currentSong() = queue.getCurrentItem()?.song

    @Test
    fun `next moves to the following song, and at the last stops without repeat`() {
        queueAt(position = 2)

        queue.getNext(ignoreRepeat = false)?.song shouldBe songs[3]
        queue.skipToNext(ignoreRepeat = false) shouldBe true
        currentSong() shouldBe songs[3]

        queue.getNext(ignoreRepeat = false).shouldBeNull()
        queue.skipToNext(ignoreRepeat = false) shouldBe false
        currentSong() shouldBe songs[3]
    }

    @Test
    fun `at the last song, next ignoring repeat wraps round to the first`() {
        queueAt(position = 3)

        queue.getNext(ignoreRepeat = true)?.song shouldBe songs[0]
        queue.skipToNext(ignoreRepeat = true) shouldBe true
        currentSong() shouldBe songs[0]
    }

    @Test
    fun `with repeat all, next at the last song wraps round to the first`() {
        queueAt(position = 3, repeatMode = RepeatMode.All)

        queue.getNext(ignoreRepeat = false)?.song shouldBe songs[0]
        queue.skipToNext(ignoreRepeat = false) shouldBe true
        currentSong() shouldBe songs[0]
    }

    @Test
    fun `with repeat one, next stays on the song, and ignoring repeat moves on`() {
        queueAt(position = 1, repeatMode = RepeatMode.One)

        queue.getNext(ignoreRepeat = false)?.song shouldBe songs[1]
        queue.skipToNext(ignoreRepeat = false) shouldBe true
        currentSong() shouldBe songs[1]

        queue.skipToNext(ignoreRepeat = true) shouldBe true
        currentSong() shouldBe songs[2]
    }

    @Test
    fun `with shuffle on, next follows the shuffled order and wraps round under repeat all`() {
        val shuffled = songs.reversed()
        queueAt(position = 2, repeatMode = RepeatMode.All, shuffleMode = ShuffleMode.On)
        currentSong() shouldBe shuffled[2]

        queue.getNext(ignoreRepeat = false)?.song shouldBe shuffled[3]
        queue.skipToNext(ignoreRepeat = false) shouldBe true
        currentSong() shouldBe shuffled[3]

        queue.skipToNext(ignoreRepeat = false) shouldBe true
        currentSong() shouldBe shuffled[0]
    }

    @Test
    fun `with shuffle on and repeat off, next stops at the end of the shuffled order`() {
        queueAt(position = 3, shuffleMode = ShuffleMode.On)

        queue.getNext(ignoreRepeat = false).shouldBeNull()
        queue.skipToNext(ignoreRepeat = false) shouldBe false
        currentSong() shouldBe songs.reversed()[3]
    }

    @Test
    fun `previous follows the shuffled order`() {
        val shuffled = songs.reversed()
        queueAt(position = 2, shuffleMode = ShuffleMode.On)

        queue.getPrevious()?.song shouldBe shuffled[1]
        queue.skipToPrevious()
        currentSong() shouldBe shuffled[1]
    }

    @Test
    fun `previous at the first song stays there, whatever the repeat mode`() {
        for (repeatMode in RepeatMode.entries) {
            queueAt(position = 0, repeatMode = repeatMode, shuffleMode = ShuffleMode.On)

            queue.getPrevious().shouldBeNull()
            queue.skipToPrevious()
            currentSong() shouldBe songs.reversed()[0]
        }
    }

    @Test
    fun `with repeat one, previous moves to the song before`() {
        queueAt(position = 2, repeatMode = RepeatMode.One)

        queue.skipToPrevious()
        currentSong() shouldBe songs[1]
    }
}
