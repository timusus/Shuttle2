package com.simplecityapps.playback.spec

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import com.simplecityapps.playback.fakes.testSong
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Spike for #345 step 1 (docs/architecture/media3-playback-design.md section 3, risk 2: "Large queues"):
 * with the whole queue as the player's playlist, is building and editing a 10k-item MediaItem list on ExoPlayer
 * cheap enough for the application (main) thread? Player only — MediaSession fan-out to controllers is measured
 * separately in step 2.
 *
 * Robolectric timings are indicative of relative cost and scaling, not device-accurate wall-clock numbers.
 *
 * Not run in CI: @Ignore below. Remove it to run locally and regenerate the numbers in the design doc.
 */
@RunWith(RobolectricTestRunner::class)
class LargeQueueSpikeTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private val players = mutableListOf<ExoPlayer>()

    @After
    fun tearDown() {
        players.forEach(ExoPlayer::release)
    }

    @Test
    @Ignore("spike: run manually - #345 step 1")
    fun `10k and 50k queue operations`() {
        val results = mutableListOf<OpResult>()

        // --- 10k: build, load, and each edit op the design doc calls out ---
        val items10k = measureBuild("build 10k MediaItems", QUEUE_10K, results)

        val player = newPlayer()
        results +=
            measure("setMediaItems+prepare (10k)", repeats = REPEATS) {
                player.setMediaItems(items10k)
                player.prepare()
            }
        player.mediaItemCount shouldBe QUEUE_10K

        val extra = mediaItem(QUEUE_10K.toLong())
        results +=
            measure("addMediaItem at end (10k)", repeats = REPEATS, action = {
                player.addMediaItem(player.mediaItemCount, extra)
            }, restore = {
                player.removeMediaItem(player.mediaItemCount - 1)
            })

        results +=
            measure("addMediaItem at index 5000 (10k)", repeats = REPEATS, action = {
                player.addMediaItem(MIDDLE_INDEX, extra)
            }, restore = {
                player.removeMediaItem(MIDDLE_INDEX)
            })

        val removed = mediaItem(QUEUE_10K.toLong() + 1)
        results +=
            measure("removeMediaItem in the middle (10k)", repeats = REPEATS, action = {
                player.removeMediaItem(MIDDLE_INDEX)
            }, restore = {
                player.addMediaItem(MIDDLE_INDEX, removed)
            })
        player.mediaItemCount shouldBe QUEUE_10K

        results +=
            measure("moveMediaItem, first to last and back (10k)", repeats = REPEATS, action = {
                player.moveMediaItem(0, player.mediaItemCount - 1)
            }, restore = {
                player.moveMediaItem(player.mediaItemCount - 1, 0)
            })
        player.mediaItemCount shouldBe QUEUE_10K

        results +=
            measure("replaceMediaItem in the middle (10k)", repeats = REPEATS) {
                player.replaceMediaItem(MIDDLE_INDEX, mediaItem(QUEUE_10K.toLong() + 2))
            }
        player.mediaItemCount shouldBe QUEUE_10K

        results +=
            measure("setShuffleOrder, build + apply (10k)", repeats = REPEATS) {
                val indices = IntArray(QUEUE_10K) { it }
                indices.shuffle()
                player.setShuffleOrder(DefaultShuffleOrder(indices, SHUFFLE_SEED))
            }

        results +=
            measure("seekTo(index 9999) (10k)", repeats = REPEATS) {
                player.seekTo(QUEUE_10K - 1, 0L)
            }
        player.currentMediaItemIndex shouldBe QUEUE_10K - 1

        // --- 50k: build + setMediaItems pair only ---
        val items50k = measureBuild("build 50k MediaItems", QUEUE_50K, results)

        val bigPlayer = newPlayer()
        results +=
            measure("setMediaItems+prepare (50k)", repeats = 1) {
                bigPlayer.setMediaItems(items50k)
                bigPlayer.prepare()
            }
        bigPlayer.mediaItemCount shouldBe QUEUE_50K

        println("\n10k queue spike (player only), median of $REPEATS unless noted:")
        println("%-42s %10s %12s".format("operation", "median ms", "heap delta"))
        results.forEach { r ->
            println("%-42s %10.2f %10d KB".format(r.name, r.medianMs, r.heapDeltaKb))
        }
    }

    private fun measureBuild(
        name: String,
        count: Int,
        results: MutableList<OpResult>
    ): List<MediaItem> {
        lateinit var items: List<MediaItem>
        results += measure(name, repeats = REPEATS) { items = buildMediaItems(count) }
        return items
    }

    private fun newPlayer(): ExoPlayer = TestExoPlayerBuilder(context)
        .setClock(FakeClock(true))
        // As production's ExoPlayer.Builder does by default: only the items around the current one are prepared.
        .setUseLazyPreparation(true)
        .build()
        .also(players::add)

    private fun buildMediaItems(count: Int): List<MediaItem> = (0 until count).map { mediaItem(it.toLong()) }

    /** mediaId = song id, a fake per-item URI (no real media is read in this spike), tag = a Song-sized object. */
    private fun mediaItem(id: Long): MediaItem {
        val song = testSong(id = id)
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri("file:///nonexistent/song$id.mp3")
            .setMimeType(song.mimeType)
            .setTag(song)
            .build()
    }

    private data class OpResult(val name: String, val medianMs: Double, val heapDeltaKb: Long)

    /** Times [action] over [repeats] runs, reporting the median wall-clock time and the heap delta of the last run. [restore] undoes [action] between runs without being timed. */
    private fun measure(
        name: String,
        repeats: Int,
        restore: (() -> Unit)? = null,
        action: () -> Unit
    ): OpResult {
        val timesNs = mutableListOf<Long>()
        var heapDeltaKb = 0L
        repeat(repeats) {
            val runtime = Runtime.getRuntime()
            System.gc()
            val before = runtime.totalMemory() - runtime.freeMemory()
            val start = System.nanoTime()
            action()
            timesNs += System.nanoTime() - start
            val after = runtime.totalMemory() - runtime.freeMemory()
            heapDeltaKb = (after - before) / 1024
            restore?.invoke()
        }
        val medianMs = timesNs.sorted()[timesNs.size / 2] / 1_000_000.0
        return OpResult(name, medianMs, heapDeltaKb)
    }

    companion object {
        private const val QUEUE_10K = 10_000
        private const val QUEUE_50K = 50_000
        private const val MIDDLE_INDEX = 5_000
        private const val REPEATS = 5
        private const val SHUFFLE_SEED = 42L
    }
}
