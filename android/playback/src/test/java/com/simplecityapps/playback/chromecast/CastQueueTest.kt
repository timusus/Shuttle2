package com.simplecityapps.playback.chromecast

import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.chromecast.CastQueue.Companion.playOrder
import com.simplecityapps.playback.chromecast.CastWindow.BEFORE
import com.simplecityapps.playback.chromecast.CastWindow.SIZE
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.toMediaItem
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** How playback is handed between the local player and a Cast receiver, the receiver played by a second player. */
@RunWith(RobolectricTestRunner::class)
class CastQueueTest {
    private val context = RuntimeEnvironment.getApplication()

    private val local: ExoPlayer = TestExoPlayerBuilder(context).build()

    private val receiver: ExoPlayer = TestExoPlayerBuilder(context).build()

    /** The receiver, as the Cast player sees it. */
    private val remote: Player =
        object : ForwardingPlayer(receiver) {
            override fun getDeviceInfo(): DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
        }

    private val provider = FakeMediaInfoProvider()

    private val streams = CastStreams(provider, EmptyCoroutineContext)

    private val converter = CastMediaItemConverter({ "10.0.0.2" }, streams, "Unknown")

    private val castQueue = CastQueue(local, converter, streams)

    @After
    fun tearDown() {
        local.release()
        receiver.release()
    }

    private fun queue(size: Int) = (1L..size).map { QueueEntry(uid = it, song = testSong(it)).toMediaItem() }

    private fun remoteQueue(size: Int) = (1L..size).map { QueueEntry(uid = it, song = remoteSong(it)).toMediaItem() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun Player.uids(): List<Long> = List(mediaItemCount) { getMediaItemAt(it).queueEntry.uid }

    private val Player.currentUid: Long get() = currentMediaItem!!.queueEntry.uid

    @Test
    fun `casting sends the window around the current item, at its position`() {
        local.setMediaItems(queue(300), 150, 30_000)
        local.playWhenReady = true

        castQueue.transferState(local, remote)

        remote.uids() shouldBe (151L - BEFORE until 151L - BEFORE + SIZE).toList()
        remote.currentUid shouldBe 151L
        remote.currentMediaItemIndex shouldBe BEFORE
        remote.currentPosition shouldBe 30_000L
        remote.playWhenReady shouldBe true
    }

    @Test
    fun `casting a short queue sends all of it`() {
        local.setMediaItems(queue(12), 2, 5_000)

        castQueue.transferState(local, remote)

        remote.uids() shouldBe (1L..12L).toList()
        remote.currentUid shouldBe 3L
        remote.playWhenReady shouldBe false
    }

    @Test
    fun `casting with shuffle on sends the queue in the order it plays`() {
        local.setMediaItems(queue(50), 0, 0)
        local.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(50, 7))
        local.shuffleModeEnabled = true
        local.seekTo(20, 1_000)
        val order = local.playOrder()

        castQueue.transferState(local, remote)

        order shouldNotBe (1L..50L).toList()
        val start = maxOf(0, order.indexOf(21L) - BEFORE)
        remote.uids() shouldBe order.drop(start)
        remote.currentUid shouldBe 21L
        remote.shuffleModeEnabled shouldBe false
    }

    @Test
    fun `coming back plays on from the receiver's item and position, paused`() {
        local.setMediaItems(queue(300), 150, 30_000)
        local.playWhenReady = true
        castQueue.transferState(local, remote)
        remote.seekTo(BEFORE + 5, 45_000)

        castQueue.transferState(remote, local)

        local.uids() shouldBe (1L..300L).toList()
        local.currentUid shouldBe 156L
        local.currentPosition shouldBe 45_000L
        local.playWhenReady shouldBe false
    }

    @Test
    fun `coming back from a receiver that never reported a position keeps the local one`() {
        local.setMediaItems(queue(20), 4, 20_000)
        receiver.setMediaItems(queue(20), 4, 0)

        castQueue.transferState(remote, local)

        local.currentUid shouldBe 5L
        local.currentPosition shouldBe 20_000L
    }

    @Test
    fun `coming back from a receiver that moved on starts its item from the beginning`() {
        local.setMediaItems(queue(20), 4, 20_000)
        receiver.setMediaItems(queue(20), 9, 0)

        castQueue.transferState(remote, local)

        local.currentUid shouldBe 10L
        local.currentPosition shouldBe 0L
    }

    @Test
    fun `coming back from a receiver holding none of the queue leaves the local player where it was`() {
        local.setMediaItems(queue(20), 4, 20_000)

        castQueue.transferState(remote, local)

        local.currentUid shouldBe 5L
        local.currentPosition shouldBe 20_000L
    }

    @Test
    fun `RS-38 a remote song is sent once its stream is resolved, as its real type, at the local position`() {
        castQueue.attach(remote)
        provider.gate = CompletableDeferred()
        local.setMediaItems(remoteQueue(30), 5, 40_000)

        castQueue.transferState(local, remote)
        idle()

        remote.mediaItemCount shouldBe 0
        provider.requests.first() shouldBe (6L to true)

        provider.gate!!.complete(Unit)
        idle()

        remote.uids() shouldBe (1L..30L).toList()
        remote.currentUid shouldBe 6L
        remote.currentPosition shouldBe 40_000L
        provider.requests.map { it.first }.toSet() shouldBe (1L..30L).toSet()
        provider.requests.all { it.second } shouldBe true
        List(remote.mediaItemCount) { converter.toMediaQueueItem(remote.getMediaItemAt(it)).media!!.contentType }.toSet() shouldBe
            setOf(FakeMediaInfoProvider.TRANSCODED)
    }

    @Test
    fun `a window goes out as far as its streams are resolved, and the rest follows`() {
        castQueue.attach(remote)
        (1L..30L).filter { it != 9L }.forEach { runBlocking { streams.resolve(listOf(remoteSong(it))) } }
        provider.gate = CompletableDeferred()
        local.setMediaItems(remoteQueue(30), 5, 0)

        castQueue.transferState(local, remote)
        idle()

        remote.uids() shouldBe (1L..8L).toList()
        remote.currentUid shouldBe 6L

        provider.gate!!.complete(Unit)
        idle()

        remote.uids() shouldBe (1L..30L).toList()
        remote.currentUid shouldBe 6L
    }
}
