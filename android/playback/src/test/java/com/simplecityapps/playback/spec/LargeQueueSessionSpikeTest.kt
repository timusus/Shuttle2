package com.simplecityapps.playback.spec

import android.os.Bundle
import android.os.Parcel
import androidx.media3.common.Timeline
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spike for #345 step 2 (docs/architecture/media3-playback-design.md, "10k queue spike"): with the whole queue as the
 * session's timeline, what does a 10k queue cost the media session? Main-thread time for a controller to connect, the
 * main-thread time the session adds to a queue change, and the bytes a remote controller is sent for the timeline
 * (androidx/media #57, #94).
 *
 * Robolectric timings are indicative of relative cost and scaling, not device-accurate wall-clock numbers. In-process
 * the session's binder calls aren't marshalled, so the sizes are measured by parcelling what a remote controller gets.
 *
 * Not run in CI: @Ignore below. Remove it to run locally and regenerate the numbers in the design doc.
 */
@RunWith(RobolectricTestRunner::class)
class LargeQueueSessionSpikeTest {
    private val releases = mutableListOf<() -> Unit>()

    @After
    fun tearDown() {
        releases.forEach { it() }
    }

    @Test
    @Ignore("spike: run manually - #345 step 2")
    fun `10k queue through the media session`() {
        val songs = (1..QUEUE_10K.toLong()).map { id -> song(id) }
        val results = mutableListOf<Pair<String, String>>()

        // Setting the queue, without a session and then with one and a controller connected to it.
        val bare = PlaybackHarness().also { releases += it::release }
        bare.run { bare.queueOperations.setQueue(songs.take(10)) }
        val bareMs = median { timeMs { bare.run { bare.queueOperations.setQueue(songs) } } }
        results += "set a 10k queue, no session" to "%.1f ms".format(bareMs)
        val bareAddMs = median {
            timeMs { bare.run { bare.queueOperations.addToQueue(listOf(song(QUEUE_10K + 1L))) } }
        }
        results += "add one song to a 10k queue, no session" to "%.1f ms".format(bareAddMs)

        val harness = SessionHarness().also { releases += it::release }
        val queue = harness.playback.queueOperations
        harness.playback.run { queue.setQueue(songs.take(10)) }
        val noControllerMs = median { timeMs { harness.playback.run { queue.setQueue(songs) } } }
        results += "set a 10k queue, session, no controller" to "%.1f ms".format(noControllerMs)
        val noControllerAddMs = median {
            timeMs { harness.playback.run { queue.addToQueue(listOf(song(QUEUE_10K + 1L))) } }
        }
        results += "add one song to a 10k queue, session, no controller" to "%.1f ms".format(noControllerAddMs)
        harness.connect()
        val sessionMs = median { timeMs { harness.playback.run { queue.setQueue(songs) } } }
        results += "set a 10k queue, session + 1 controller" to "%.1f ms".format(sessionMs)
        harness.playback.appPlayer.mediaItemCount shouldBe QUEUE_10K

        val addMs = median {
            timeMs { harness.playback.run { queue.addToQueue(listOf(song(QUEUE_10K + 1L))) } }
        }
        results += "add one song to a 10k queue, session + 1 controller" to "%.1f ms".format(addMs)

        // A controller connecting to a session holding the 10k queue: each is a new controller, as Android Auto or
        // SystemUI's is.
        val connectMs = median { timeMs { harness.connect() } }
        results += "connect a controller, 10k queue" to "%.1f ms".format(connectMs)
        val browser = harness.connect()
        browser.mediaItemCount shouldBe harness.playback.appPlayer.mediaItemCount

        // What a remote controller is sent for the timeline: its windows and periods, each a bundle, sent in chunks
        // (BundleListRetriever) rather than in the one transaction.
        val timeline = harness.playback.appPlayer.currentTimeline
        val bundleMs = median {
            timeMs {
                (0 until timeline.windowCount).forEach { index -> timeline.getWindow(index, Timeline.Window()).toBundle() }
                (0 until timeline.periodCount).forEach { index -> timeline.getPeriod(index, Timeline.Period()).toBundle() }
            }
        }
        results += "bundle the timeline's windows and periods" to "%.1f ms".format(bundleMs)
        val windowBytes = (0 until timeline.windowCount).map { index -> parcelSize(timeline.getWindow(index, Timeline.Window()).toBundle()) }
        val periodBytes = (0 until timeline.periodCount).map { index -> parcelSize(timeline.getPeriod(index, Timeline.Period()).toBundle()) }
        results += "timeline window bundles, total" to "%d KB".format(windowBytes.sum() / 1024)
        results += "timeline period bundles, total" to "%d KB".format(periodBytes.sum() / 1024)
        results += "one window bundle, max" to "%d B".format(windowBytes.max())
        results += "timeline bundle itself (windows by binder)" to "%d B".format(parcelSize(timeline.toBundle()))

        println("\n10k queue spike (media session), median of $REPEATS unless noted:")
        results.forEach { (name, value) -> println("%-56s %12s".format(name, value)) }
    }

    private fun parcelSize(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun timeMs(block: () -> Unit): Double {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000.0
    }

    private fun median(measure: () -> Double): Double = List(REPEATS) { measure() }.sorted()[REPEATS / 2]

    private companion object {
        const val QUEUE_10K = 10_000
        const val REPEATS = 5
    }
}
