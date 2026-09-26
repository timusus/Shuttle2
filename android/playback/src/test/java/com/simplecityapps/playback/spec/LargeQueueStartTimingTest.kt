package com.simplecityapps.playback.spec

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.longSong
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Song
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * How long a long queue takes from being set to playing, through PlaybackFacade and QueueFacade over the real
 * player (docs/architecture/media3-playback-design.md, "10k queue spike"), with lazy preparation (production's
 * default) and with every item prepared, for comparison.
 *
 * Robolectric timings are indicative of relative cost and scaling, not device-accurate wall-clock numbers.
 *
 * Not run in CI: @Ignore below. Remove it to run locally and regenerate the numbers in the design doc.
 */
@RunWith(RobolectricTestRunner::class)
class LargeQueueStartTimingTest {
    private enum class Scenario(val label: String) {
        SetQueue("set the queue, play"),
        Restore("restore at the middle, 1 s in, play"),
        AddAll("add all to a playing queue"),
        ShuffleAll("shuffle all, play")
    }

    @Ignore("measurement: run manually - #345 step 1")
    @Test
    fun `time from setting a long queue to playing`() {
        // Warm up class loading and the JIT before timing anything.
        time(Scenario.SetQueue, songs(SIZES.first()), lazyPreparation = true)

        val rows =
            listOf(true, false).flatMap { lazy ->
                Scenario.entries.map { scenario ->
                    val preparation = if (lazy) "lazy" else "eager"
                    val times =
                        SIZES.map { size ->
                            if (lazy || size <= EAGER_MAX_SIZE) {
                                time(scenario, songs(size), lazy).also { System.err.println("$preparation, ${scenario.label}, $size: $it ms") }
                            } else {
                                null
                            }
                        }
                    Triple(preparation, scenario.label, times)
                }
            }

        println("\nTime from setting the queue to playing, ms:")
        println("%-6s %-38s".format("", "") + SIZES.joinToString("") { "%9d".format(it) })
        rows.forEach { (preparation, label, times) ->
            println("%-6s %-38s".format(preparation, label) + times.joinToString("") { "%9s".format(it ?: "-") })
        }
    }

    /** Runs [scenario] on a new player, returning the milliseconds from the queue change to playing. */
    private fun time(
        scenario: Scenario,
        songs: List<Song>,
        lazyPreparation: Boolean
    ): Long {
        val harness = PlaybackHarness(lazyPreparation = lazyPreparation)
        val playback = harness.playbackOperations
        val queue = harness.queueOperations
        try {
            if (scenario == Scenario.AddAll) {
                harness.run { playback.addToQueue(listOf(longSong(id = 0))) }
                harness.runUntil { playback.playbackState() == PlaybackState.Playing }
            }
            val playOnLoad: (Result<Any?>) -> Unit = { result -> result.onSuccess { playback.play() } }

            val start = System.nanoTime()
            when (scenario) {
                Scenario.SetQueue -> {
                    harness.run { queue.setQueue(songs) }
                    playback.load(completion = playOnLoad)
                }

                Scenario.Restore -> {
                    harness.run { queue.setQueue(songs, position = songs.size / 2) }
                    playback.load(seekPosition = RESTORE_POSITION_MS, completion = playOnLoad)
                }

                Scenario.AddAll -> harness.run { playback.addToQueue(songs) }

                Scenario.ShuffleAll -> harness.run { playback.shuffle(songs, playOnLoad) }
            }
            harness.runUntil {
                queue.getSize() >= songs.size && playback.playbackState() == PlaybackState.Playing
            }
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            if (scenario == Scenario.ShuffleAll) check(queue.getShuffleMode() == ShuffleMode.On)
            return elapsedMs
        } finally {
            harness.release()
        }
    }

    private fun songs(count: Int): List<Song> = (1..count.toLong()).map { song(it) }

    companion object {
        private val SIZES = listOf(300, 1_000, 2_000, 5_000, 10_000)

        /** Preparing every item of a 5k queue runs the test JVM (512 MB heap) out of memory. */
        private const val EAGER_MAX_SIZE = 2_000
        private const val RESTORE_POSITION_MS = 1_000
    }
}
