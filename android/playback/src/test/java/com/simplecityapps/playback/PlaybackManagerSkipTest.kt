package com.simplecityapps.playback

import com.simplecityapps.playback.fakes.FakePlayback
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testPlaybackManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * skipToPrev() re-starts the current track (seeks to zero) when more than 2s of progress has
 * elapsed, and only actually skips to the previous queue item within the first 2s, or when
 * forced. onTrackEnded() takes a lighter path (loadNext only) when the player already auto
 * advanced; otherwise it drives a full skip.
 */
class PlaybackManagerSkipTest {
    private val events = mutableListOf<String>()
    private val queueWatcher = QueueWatcher()
    private val queueManager = QueueManager(queueWatcher, GeneralPreferenceManager(FakeSharedPreferences()))
    private val playback = FakePlayback("A", events = events)

    private lateinit var playbackManager: PlaybackManager

    @Before
    fun setUp() {
        playbackManager = testPlaybackManager(
            exoplayerPlayback = playback,
            queueWatcher = queueWatcher,
            queueManager = queueManager
        )
        runBlocking { queueManager.setQueue((1L..3L).map { createSong(it) }, position = 1) }
        events.clear()
    }

    @Test
    fun `skipToPrev within 2s of progress skips to the previous item`() {
        playback.progressMs = 1_500

        playbackManager.skipToPrev()

        queueManager.getCurrentItem()!!.song.id shouldBe 1L
        events shouldBe listOf("A load Song1 seek 0")
    }

    @Test
    fun `skipToPrev after 2s of progress restarts the current item instead of skipping`() {
        playback.progressMs = 2_500

        playbackManager.skipToPrev()

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        events shouldBe listOf("A seek 0")
    }

    @Test
    fun `skipToPrev with force skips to the previous item even after 2s of progress`() {
        playback.progressMs = 2_500

        playbackManager.skipToPrev(force = true)

        queueManager.getCurrentItem()!!.song.id shouldBe 1L
        events shouldBe listOf("A load Song1 seek 0")
    }

    @Test
    fun `skipToPrev treats unknown progress as within 2s and skips to the previous item`() {
        // Pins current behaviour; see #250
        playback.progressMs = null

        playbackManager.skipToPrev()

        queueManager.getCurrentItem()!!.song.id shouldBe 1L
        events shouldBe listOf("A load Song1 seek 0")
    }

    @Test
    fun `a skip superseded by a later one does not play when its load completes`() {
        // #293: rapid skips on a slow provider.
        playbackManager.skipToPrev(force = true)
        playbackManager.skipToNext()
        events.clear()

        playback.completeLoad()

        events.shouldBeEmpty()

        playback.completeLoad()

        events shouldBe listOf("A play")
        queueManager.getCurrentItem()!!.song.id shouldBe 2L
    }

    @Test
    fun `skipToPrev during a load goes by the loading track's position, not the old one's`() {
        playback.progressMs = 170_000
        playbackManager.skipToNext()
        events.clear()

        playbackManager.skipToPrev()

        queueManager.getCurrentItem()!!.song.id shouldBe 2L
        events shouldBe listOf("A load Song2 seek 0")
    }

    @Test
    fun `onTrackEnded with auto-advance only loads the next item, it does not call play`() {
        playback.callback!!.onTrackEnded(trackWentToNext = true)

        queueManager.getCurrentItem()!!.song.id shouldBe 3L
        events shouldBe listOf("A loadNext null")
    }

    @Test
    fun `onTrackEnded without auto-advance drives a full skip and plays once loaded`() {
        playback.callback!!.onTrackEnded(trackWentToNext = false)

        queueManager.getCurrentItem()!!.song.id shouldBe 3L
        events shouldBe listOf("A load Song3 seek 0")

        playback.completeLoad()

        events shouldBe listOf("A load Song3 seek 0", "A play")
    }

    private fun createSong(id: Long) = Song(
        id = id,
        name = "Song$id",
        albumArtist = null,
        artists = emptyList(),
        album = null,
        track = null,
        disc = null,
        duration = 180_000,
        date = null,
        genres = emptyList(),
        path = "/music/song$id.mp3",
        size = 0,
        mimeType = "audio/mpeg",
        lastModified = null,
        lastPlayed = null,
        lastCompleted = null,
        playCount = 0,
        playbackPosition = 0,
        blacklisted = false,
        mediaProvider = MediaProviderType.Shuttle,
        lyrics = null,
        grouping = null,
        bitRate = null,
        bitDepth = null,
        sampleRate = null,
        channelCount = null
    )
}
