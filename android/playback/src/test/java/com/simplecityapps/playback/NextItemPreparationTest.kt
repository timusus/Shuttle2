package com.simplecityapps.playback

import com.simplecityapps.playback.exoplayer.ExoPlayerPlayback
import com.simplecityapps.playback.exoplayer.ResolvedMedia
import com.simplecityapps.playback.fakes.FakePlayer
import com.simplecityapps.playback.fakes.FakePlayerFactory
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * The gapless next item a real [ExoPlayerPlayback] queues, driven through [PlaybackManager]: it's
 * queued once a load succeeds and after an auto-advance, and a queue change while it's still
 * resolving (a remote stream) queues only the new next item (#315). Resolving a song suspends until
 * the test releases its gate; everything else runs inline.
 */
class NextItemPreparationTest {
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    private val playerFactory = FakePlayerFactory()
    private val exoPlayerPlayback =
        ExoPlayerPlayback(
            playerFactory = playerFactory,
            mediaResolver = { song ->
                gates[song.name]?.await()
                ResolvedMedia(uri = song.path, mimeType = song.mimeType, isRemote = false)
            }
        )
    private val queueManager = QueueManager(GeneralPreferenceManager(FakeSharedPreferences()))
    private lateinit var playbackManager: PlaybackManager

    private val player: FakePlayer get() = playerFactory.latest

    @Before
    fun setUp() {
        playbackManager = testPlaybackManager(exoplayerPlayback = exoPlayerPlayback, queueManager = queueManager)
        runBlocking { queueManager.setQueue((1L..4L).map { testSong(it) }) }
    }

    /** Holds [id]'s resolve until the returned gate is completed. */
    private fun gate(id: Long) = CompletableDeferred<Unit>().also { gates["Song$id"] = it }

    private fun queuedUris() = player.playlist.map { it.uri }

    private fun uri(id: Long) = "/music/song$id.mp3"

    @Test
    fun `the next item is queued once a load succeeds`() {
        playbackManager.load { }

        queuedUris() shouldBe listOf(uri(1), uri(2))
    }

    @Test
    fun `the new next item is queued after an auto-advance`() {
        playbackManager.load { }

        player.playToEnd()

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        queuedUris() shouldBe listOf(uri(2), uri(3))
    }

    @Test
    fun `a queue change while the next item resolves queues only the new next item`() {
        val song2Resolved = gate(2)
        playbackManager.load { }

        playbackManager.removeQueueItem(queueManager.getQueue()[1])
        song2Resolved.complete(Unit)

        queuedUris() shouldBe listOf(uri(1), uri(3))
        player.commands shouldNotContain "addMediaItem ${uri(2)}"
    }
}
