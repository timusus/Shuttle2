package com.simplecityapps.playback

import android.os.Looper
import androidx.media3.common.util.Clock
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.RobolectricUtil
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
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
    private val player: ExoPlayer = TestExoPlayerBuilder(RuntimeEnvironment.getApplication()).setClock(FakeClock(true)).build()

    private var gaveUp = 0

    private val loader = ItemLoader(player, player, giveUp = { gaveUp++ }).also(player::addListener)

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
        RobolectricUtil.runMainLooperUntil({ result != null }, 10_000, Clock.DEFAULT)
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
    fun `abandoning a load fails it`() {
        queue(playable)
        var result: Result<Boolean>? = null
        loader.load(0, skipUnloadable = true) { result = it }

        loader.abandon()

        result?.isFailure shouldBe true
        loader.isLoading shouldBe false
    }
}
