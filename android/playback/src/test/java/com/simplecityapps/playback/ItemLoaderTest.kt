package com.simplecityapps.playback

import android.net.Uri
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.playback.queue.uri
import com.simplecityapps.playback.spec.ClockDriver
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Loading the current item and skipping ones that fail, on a real player: the behaviour is RS-10, RS-23 and RS-56. */
// Robolectric: drives a real TestExoPlayerBuilder-backed ExoPlayer.
@RunWith(RobolectricTestRunner::class)
class ItemLoaderTest {
    private val clock = FakeClock(false)

    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(clock).build()

    private val driver = ClockDriver(clock, player)

    private var gaveUp = 0

    /** A song streamed from a transcode the server drops when another starts (as Plex does): its own file, as that's what tells it apart. */
    private val transcode = PlaybackHarness.song(3, file = PlaybackHarness.TONE_3S)

    private val loader = ItemLoader(player, player, giveUp = { gaveUp++ }, isReplaceableTranscode = { uri -> uri == transcode.uri() }).also(player::addListener)

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private val failures = mutableListOf<Song>().also { failures -> scope.launch { loader.failureFlow.collect { failures += it } } }

    private val missing = testSong(1, path = PlaybackHarness.MISSING_FILE_URI, mimeType = "audio/wav")

    private val playable = PlaybackHarness.song(2)

    @After
    fun tearDown() {
        scope.cancel()
        player.release()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun queue(vararg songs: Song) = player.setMediaItems(songs.map { it.toQueueEntry().toMediaItem() })

    private fun load(skipUnloadable: Boolean): Result<Boolean> {
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable) { result = it }
        loader.isLoading shouldBe true
        driver.runUntil { result != null }
        return checkNotNull(result)
    }

    @Test
    fun `an item that loads is ready at the first attempt`() {
        queue(playable)

        load(skipUnloadable = true) shouldBe Result.success(true)

        loader.isLoading shouldBe false
        loader.isCurrentReady shouldBe true
    }

    @Test
    fun `an item that fails to load is reported and skipped for the next`() {
        queue(missing, playable)

        load(skipUnloadable = true) shouldBe Result.success(false)

        player.currentMediaItemIndex shouldBe 1
        failures shouldBe listOf(missing)
        gaveUp shouldBe 0
    }

    @Test
    fun `a load that doesn't skip leaves a failed item current, and gives up`() {
        queue(missing, playable)

        load(skipUnloadable = false).isFailure shouldBe true

        player.currentMediaItemIndex shouldBe 0
        loader.isCurrentReady shouldBe false
        gaveUp shouldBe 1
    }

    @Test
    fun `with nothing after it to skip to, a failed load gives up`() {
        queue(playable, missing)
        player.seekTo(1, 0)

        load(skipUnloadable = true).isFailure shouldBe true

        gaveUp shouldBe 1
    }

    @Test
    fun `a load replaced before its item is ready is dropped - and the new one completes`() {
        queue(playable)
        var replaced: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { replaced = it }

        load(skipUnloadable = true) shouldBe Result.success(true)

        (replaced?.exceptionOrNull() is CancellationException) shouldBe true
    }

    @Test
    fun `a 404 on a replaced transcode opens it again at the position - playing on`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.currentPosition >= 500 }
        val position = player.currentPosition

        failWithNotFound()

        player.playerError shouldBe null
        player.playWhenReady shouldBe true
        driver.runUntil { player.isPlaying }
        player.currentMediaItemIndex shouldBe 0
        player.currentPosition shouldBeGreaterThanOrEqual position
        failures shouldBe emptyList()
        gaveUp shouldBe 0
    }

    @Test
    fun `a transcode that 404s again at the same position gives up`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }

        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0

        failWithNotFound()

        player.playbackState shouldBe Player.STATE_IDLE
        failures shouldBe listOf(transcode)
        gaveUp shouldBe 1
    }

    @Test
    fun `a transcode replaced again at the same position after playback moved on is opened again`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }

        player.seekTo(0, 2_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()

        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0
        failures shouldBe emptyList()
    }

    @Test
    fun `a 404 at another position than the last re-open is opened again`() {
        queue(transcode)
        load(skipUnloadable = true)
        player.seekTo(0, 1_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()
        driver.runUntil { player.playbackState == Player.STATE_READY }

        player.seekTo(0, 2_000)
        driver.runUntil { player.playbackState == Player.STATE_READY }
        failWithNotFound()

        driver.runUntil { player.playbackState == Player.STATE_READY }
        gaveUp shouldBe 0
    }

    @Test
    fun `a 404 on a transcode not yet ready opens it again rather than skipping it`() {
        queue(transcode, playable)
        player.play()
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { result = it }

        failWithNotFound()
        driver.runUntil { result != null }

        result shouldBe Result.success(true)
        player.currentMediaItemIndex shouldBe 0
        failures shouldBe emptyList()
    }

    @Test
    fun `a 404 on a stream that isn't a replaceable transcode stops playback as before`() {
        queue(playable)
        load(skipUnloadable = true)
        player.play()
        driver.runUntil { player.isPlaying }

        failWithNotFound()

        player.playbackState shouldBe Player.STATE_IDLE
        failures shouldBe listOf(playable)
        gaveUp shouldBe 1
    }

    /** Fails the player as a 404 on a stream's segment does, from its playback thread. */
    private fun failWithNotFound() {
        val notFound = HttpDataSource.InvalidResponseCodeException(404, "Not Found", null, emptyMap(), DataSpec(Uri.parse("https://plex/segment.ts")), ByteArray(0))
        player.createMessage { _, _ -> throw ExoPlaybackException.createForSource(notFound, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) }.send()
        driver.idle()
    }

    @Test
    fun `abandoning a load fails it`() {
        queue(playable)
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { result = it }

        loader.abandon()

        result?.isFailure shouldBe true
        loader.isLoading shouldBe false
    }
}
