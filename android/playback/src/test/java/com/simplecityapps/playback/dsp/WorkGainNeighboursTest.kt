package com.simplecityapps.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ForwardingTimeline
import com.simplecityapps.playback.dsp.crossfade.tailGainMode
import com.simplecityapps.playback.dsp.replaygain.ReplayGain
import com.simplecityapps.playback.dsp.replaygain.ReplayGainAudioProcessor
import com.simplecityapps.playback.dsp.replaygain.ReplayGainMode
import com.simplecityapps.playback.dsp.replaygain.isWorkMovementAt
import com.simplecityapps.playback.fakes.FakePlaylistTimeline
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.fakes.withFakeUriStatics
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import org.junit.Test

class WorkGainNeighboursTest {
    private fun movement(id: Long) = testSong(id = id, replayGainTrack = -6.0, replayGainAlbum = 3.0).copy(
        work = "Symphony No. 5",
        composer = "Beethoven",
        genres = listOf("Classical")
    )

    private fun lone(id: Long) = testSong(id = id, replayGainTrack = -6.0, replayGainAlbum = 3.0)

    /** [songs] in playlist order, played in [playOrder] when shuffled. */
    private fun timeline(
        songs: List<Song>,
        playOrder: List<Int>
    ): Timeline = withFakeUriStatics {
        val items = songs.mapIndexed { i, song -> QueueEntry(uid = i + 1L, song = song, replayGain = ReplayGain(-6.0, 3.0)).toMediaItem() }
        ShuffledTimeline(FakePlaylistTimeline(items), playOrder)
    }

    @Test
    fun `playlist order decides when not shuffled`() {
        val timeline = timeline(listOf(movement(1), movement(2), lone(3)), playOrder = listOf(0, 2, 1))

        isWorkMovementAt(timeline, 0, shuffled = false) shouldBe true
    }

    @Test
    fun `shuffle order decides when shuffled, so a shuffle by work keeps album gain`() {
        val timeline = timeline(listOf(movement(1), lone(2), movement(3), lone(4)), playOrder = listOf(0, 2, 3, 1))

        isWorkMovementAt(timeline, 0, shuffled = false) shouldBe false
        isWorkMovementAt(timeline, 0, shuffled = true) shouldBe true
        isWorkMovementAt(timeline, 2, shuffled = true) shouldBe true
    }

    @Test
    fun `a song shuffle that splits a work gives its movements track gain`() {
        val timeline = timeline(listOf(movement(1), movement(2), lone(3)), playOrder = listOf(0, 2, 1))

        isWorkMovementAt(timeline, 0, shuffled = true) shouldBe false
        isWorkMovementAt(timeline, 1, shuffled = true) shouldBe false
    }

    @Test
    fun `a tail decodes at the mode its main stream played at`() {
        val timeline = timeline(listOf(movement(1), movement(2), lone(3)), playOrder = listOf(0, 1, 2))
        val processor = ReplayGainAudioProcessor(ReplayGainMode.Track).apply { albumGainForWorks = true }
        val window = Timeline.Window()

        tailGainMode(processor, timeline, timeline.getWindow(1, window).mediaItem) shouldBe ReplayGainMode.Album
        tailGainMode(processor, timeline, timeline.getWindow(2, window).mediaItem) shouldBe ReplayGainMode.Track

        processor.albumGainForWorks = false
        tailGainMode(processor, timeline, timeline.getWindow(1, window).mediaItem) shouldBe ReplayGainMode.Track
    }

    @Test
    fun `a lone song in a long queue reads only its own window and its neighbours`() {
        val songs = List(500) { lone(it + 1L) }
        val counting = CountingTimeline(timeline(songs, playOrder = songs.indices.toList()))

        isWorkMovementAt(counting, 250, shuffled = false) shouldBe false

        (counting.windowsRead <= 3) shouldBe true
    }

    @Test
    fun `a song without an album gain reads no neighbours`() {
        val songs = List(500) { movement(it + 1L).copy(replayGainAlbum = null) }
        val counting = CountingTimeline(timeline(songs, playOrder = songs.indices.toList()))

        isWorkMovementAt(counting, 250, shuffled = false) shouldBe false

        counting.windowsRead shouldBe 1
    }

    private class CountingTimeline(base: Timeline) : ForwardingTimeline(base) {
        var windowsRead = 0

        override fun getWindow(
            windowIndex: Int,
            window: Timeline.Window,
            defaultPositionProjectionUs: Long
        ): Timeline.Window {
            windowsRead++
            return super.getWindow(windowIndex, window, defaultPositionProjectionUs)
        }
    }

    /** [base] with a shuffle order: [playOrder] lists window indices in the order they play. */
    private class ShuffledTimeline(
        base: Timeline,
        private val playOrder: List<Int>
    ) : ForwardingTimeline(base) {
        override fun getNextWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean
        ): Int {
            if (!shuffleModeEnabled) return super.getNextWindowIndex(windowIndex, repeatMode, false)
            return playOrder.getOrNull(playOrder.indexOf(windowIndex) + 1) ?: C.INDEX_UNSET
        }

        override fun getPreviousWindowIndex(
            windowIndex: Int,
            repeatMode: Int,
            shuffleModeEnabled: Boolean
        ): Int {
            if (!shuffleModeEnabled) return super.getPreviousWindowIndex(windowIndex, repeatMode, false)
            return playOrder.getOrNull(playOrder.indexOf(windowIndex) - 1) ?: C.INDEX_UNSET
        }
    }
}
