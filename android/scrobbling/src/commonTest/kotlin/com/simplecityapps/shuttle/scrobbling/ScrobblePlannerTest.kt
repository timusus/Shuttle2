package com.simplecityapps.shuttle.scrobbling

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.scrobbling.ScrobblePlanner.Decision
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.math.min
import kotlin.test.Test

class ScrobblePlannerTest {
    private val song = createSong(id = 1, duration = 200_000)
    private val otherSong = createSong(id = 2, duration = 180_000)
    private val serverSong = createSong(id = 3, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)

    private var elapsedMs = 0L
    private var epochMs = START_EPOCH_MS
    private var positionMs = 0

    private val planner = ScrobblePlanner(
        isServerSong = { it.mediaProvider.remote },
        elapsedRealtimeMs = { elapsedMs },
        currentTimeMs = { epochMs }
    )

    private fun passTime(ms: Long) {
        elapsedMs += ms
        epochMs += ms
    }

    private fun changeItem(
        song: Song?,
        uid: Long = song?.id?.plus(10) ?: 0
    ): Decision? {
        positionMs = 0
        return planner.onCurrentItemChanged(song?.let { QueueItem(uid, it, isCurrent = true) })
    }

    /** Makes [song] the current item and starts playing it from the start. */
    private fun startPlaying(song: Song = this.song): Decision? {
        changeItem(song)
        return planner.onStateChanged(PlaybackState.Playing)
    }

    /** A jump to [positionMs] (a seek, or the republish on a track change), taking no time. */
    private fun jumpTo(positionMs: Int): Decision? {
        this.positionMs = positionMs
        return planner.onProgress(positionMs)
    }

    /** Plays on for [ms] of real time at [speed], a tick every [tickMs], returning every decision on the way. */
    private fun listen(
        ms: Long,
        speed: Float = 1f,
        tickMs: Long = 100
    ): List<Decision> {
        val decisions = mutableListOf<Decision>()
        var remainingMs = ms
        while (remainingMs > 0) {
            val stepMs = min(tickMs, remainingMs)
            passTime(stepMs)
            positionMs += (stepMs * speed).toInt()
            planner.onProgress(positionMs)?.let { decisions += it }
            remainingMs -= stepMs
        }
        return decisions
    }

    private fun scrobble(
        song: Song = this.song,
        startedAtEpochMs: Long = START_EPOCH_MS
    ) = Decision.Scrobble(song, startedAtEpochMs / 1000)

    // Now playing

    @Test
    fun `playing an item announces it as now playing`() {
        startPlaying() shouldBe Decision.NowPlaying(song)
    }

    @Test
    fun `a restored item that is only loaded is not announced or counted`() {
        changeItem(song).shouldBeNull()
        planner.onStateChanged(PlaybackState.Paused).shouldBeNull()
        jumpTo(60_000).shouldBeNull()
        passTime(200_000)
        jumpTo(60_000).shouldBeNull()
    }

    @Test
    fun `resuming a restored item announces it then`() {
        changeItem(song)
        planner.onStateChanged(PlaybackState.Paused)
        jumpTo(60_000)
        passTime(5_000)

        planner.onStateChanged(PlaybackState.Playing) shouldBe Decision.NowPlaying(song)
    }

    @Test
    fun `now playing is announced once per play - not on each resume`() {
        startPlaying()
        listen(10_000)

        planner.onStateChanged(PlaybackState.Paused).shouldBeNull()
        planner.onStateChanged(PlaybackState.Loading).shouldBeNull()
        planner.onStateChanged(PlaybackState.Playing).shouldBeNull()
    }

    @Test
    fun `an item that becomes current while playing is announced straight away`() {
        startPlaying()
        listen(10_000)

        changeItem(otherSong) shouldBe Decision.NowPlaying(otherSong)
    }

    // Thresholds

    @Test
    fun `a song of 30 seconds or less is never scrobbled`() {
        val shortSong = createSong(id = 4, duration = 30_000)
        startPlaying(shortSong)

        listen(30_000).shouldBeEmpty()
    }

    @Test
    fun `a song just over 30 seconds is scrobbled at half its length`() {
        val shortSong = createSong(id = 4, duration = 30_002)
        startPlaying(shortSong)

        listen(15_000).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(shortSong))
    }

    @Test
    fun `a song is scrobbled once half of it has been listened to`() {
        startPlaying()

        listen(99_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `a long song is scrobbled after 4 minutes`() {
        val longSong = createSong(id = 4, duration = 600_000)
        startPlaying(longSong)

        listen(239_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(longSong))
    }

    @Test
    fun `a scrobble is stamped with the wall-clock start of the play`() {
        passTime(42_000)
        startPlaying()

        listen(100_000) shouldBe listOf(scrobble(startedAtEpochMs = START_EPOCH_MS + 42_000))
    }

    @Test
    fun `a play is scrobbled at most once`() {
        startPlaying()
        listen(100_000)

        listen(99_000).shouldBeEmpty()
    }

    @Test
    fun `a skip before the threshold scrobbles nothing`() {
        startPlaying()
        listen(99_000).shouldBeEmpty()

        changeItem(otherSong) shouldBe Decision.NowPlaying(otherSong)
        listen(1_000).shouldBeEmpty()
    }

    // Listened time

    @Test
    fun `time paused does not count - and resuming after a long pause is not a seek`() {
        startPlaying()
        listen(60_000)
        planner.onStateChanged(PlaybackState.Paused)
        passTime(300_000)
        jumpTo(positionMs).shouldBeNull()
        planner.onStateChanged(PlaybackState.Playing)

        listen(39_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `buffering mid-song does not count`() {
        startPlaying()
        listen(60_000)
        planner.onStateChanged(PlaybackState.Loading)
        passTime(5_000)
        planner.onStateChanged(PlaybackState.Playing)

        listen(39_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `a seek forward does not count as listened`() {
        startPlaying()
        listen(10_000)
        jumpTo(150_000).shouldBeNull()

        listen(50_000).shouldBeEmpty()
        listen(39_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `a seek forward during a tick interval does not count as listened`() {
        startPlaying()
        listen(10_000)
        passTime(100)
        jumpTo(positionMs + 30_000).shouldBeNull()

        listen(89_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `a seek back does not count - but listening again does`() {
        startPlaying()
        listen(80_000)
        jumpTo(20_000).shouldBeNull()

        listen(19_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `a seek while paused does not count as listened`() {
        startPlaying()
        listen(10_000)
        planner.onStateChanged(PlaybackState.Paused)
        passTime(1_000)
        jumpTo(150_000).shouldBeNull()
        planner.onStateChanged(PlaybackState.Playing)

        listen(89_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble())
    }

    @Test
    fun `at 2x a song is scrobbled after half its length in track time`() {
        startPlaying()
        planner.onPlaybackSpeedChanged(2f)

        listen(49_900, speed = 2f).shouldBeEmpty()
        listen(100, speed = 2f) shouldBe listOf(scrobble())
    }

    @Test
    fun `at 2x coarse ticks count as listened - where at 1x the same jump is a seek`() {
        startPlaying()
        planner.onPlaybackSpeedChanged(2f)
        listen(50_000, speed = 2f, tickMs = 5_000) shouldBe listOf(scrobble())

        startPlaying(otherSong)
        planner.onPlaybackSpeedChanged(1f)
        passTime(5_000)
        jumpTo(10_000).shouldBeNull()
        listen(89_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(otherSong, startedAtEpochMs = START_EPOCH_MS + 50_000))
    }

    @Test
    fun `slow ticks at 1x - like a Cast session's - count as listened`() {
        startPlaying()

        listen(100_000, tickMs = 1_500) shouldBe listOf(scrobble())
    }

    @Test
    fun `a speed change between ticks is timed at each speed`() {
        startPlaying()
        passTime(3_000)
        planner.onPlaybackSpeedChanged(3f)
        passTime(3_000)
        // 3 s at 1x and 3 s at 3x: 12 s of track, which 1x (6 s) or 3x (18 s) alone would call a seek.
        jumpTo(12_000).shouldBeNull()

        listen(88_000 / 3 - 100, speed = 3f).shouldBeEmpty()
        listen(200, speed = 3f) shouldBe listOf(scrobble())
    }

    // Repeat and play-through

    @Test
    fun `a repeat-one replay is a new play - announced and scrobbled again`() {
        startPlaying()
        listen(200_000) shouldBe listOf(scrobble())
        planner.onTrackEnded(song).shouldBeNull()

        jumpTo(0).shouldBeNull()
        listen(100) shouldBe listOf(Decision.NowPlaying(song))
        listen(99_900) shouldBe listOf(scrobble(startedAtEpochMs = START_EPOCH_MS + 200_100))
    }

    @Test
    fun `a late tick after a track ends starts nothing`() {
        startPlaying()
        listen(199_900)
        planner.onTrackEnded(song)

        jumpTo(200_000).shouldBeNull()
        passTime(100)
        jumpTo(200_000).shouldBeNull()
    }

    @Test
    fun `a seek back to the start mid-song is the same play`() {
        startPlaying()
        listen(120_000) shouldBe listOf(scrobble())

        jumpTo(0).shouldBeNull()
        listen(200_000).shouldBeEmpty()
    }

    @Test
    fun `playing an ended item again after a seek back is a new play`() {
        startPlaying()
        listen(200_000)
        planner.onTrackEnded(song)
        planner.onStateChanged(PlaybackState.Paused)

        jumpTo(0).shouldBeNull()
        planner.onStateChanged(PlaybackState.Playing).shouldBeNull()
        listen(100) shouldBe listOf(Decision.NowPlaying(song))
    }

    @Test
    fun `the tick to the next item's start before the item changes does not replay the one that ended`() {
        startPlaying()
        listen(200_000)
        planner.onTrackEnded(song)
        // On an automatic transition the progress republish arrives before the new current item.
        jumpTo(0).shouldBeNull()

        changeItem(otherSong) shouldBe Decision.NowPlaying(otherSong)
        listen(89_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(otherSong, startedAtEpochMs = START_EPOCH_MS + 200_000))
    }

    @Test
    fun `a track end for another song is ignored`() {
        startPlaying()
        listen(50_000)

        planner.onTrackEnded(otherSong).shouldBeNull()
        listen(50_000) shouldBe listOf(scrobble())
    }

    // Crossfade

    @Test
    fun `a crossfade that cuts a song short keeps the thresholds on the song's own duration`() {
        // 35 s long, cut to 25 s by a 10 s crossfade: over the 30 s floor, and half of it is still reached.
        val shortSong = createSong(id = 4, duration = 35_000)
        startPlaying(shortSong)

        listen(17_400).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(shortSong))
        listen(7_500).shouldBeEmpty()
        planner.onTrackEnded(shortSong)
        changeItem(otherSong) shouldBe Decision.NowPlaying(otherSong)
    }

    @Test
    fun `a late tick from the outgoing item does not count towards the next`() {
        startPlaying()
        listen(190_000)

        changeItem(otherSong)
        passTime(100)
        jumpTo(190_100).shouldBeNull()
        passTime(100)
        jumpTo(200).shouldBeNull()

        listen(89_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(otherSong, startedAtEpochMs = START_EPOCH_MS + 190_000))
    }

    // Server streams

    @Test
    fun `a server song is skipped by default`() {
        startPlaying(serverSong).shouldBeNull()

        listen(200_000).shouldBeEmpty()
    }

    @Test
    fun `a server song is scrobbled when scrobbling server streams is on`() {
        planner.onScrobbleServerStreamsChanged(true).shouldBeNull()

        startPlaying(serverSong) shouldBe Decision.NowPlaying(serverSong)
        listen(100_000) shouldBe listOf(scrobble(serverSong))
    }

    @Test
    fun `turning server streams on mid-song starts a play there`() {
        startPlaying(serverSong)
        listen(50_000)

        planner.onScrobbleServerStreamsChanged(true) shouldBe Decision.NowPlaying(serverSong)
        listen(99_900).shouldBeEmpty()
        listen(100) shouldBe listOf(scrobble(serverSong, startedAtEpochMs = START_EPOCH_MS + 50_000))
    }

    @Test
    fun `turning server streams on while paused starts the play on resume`() {
        startPlaying(serverSong)
        planner.onStateChanged(PlaybackState.Paused)

        planner.onScrobbleServerStreamsChanged(true).shouldBeNull()
        planner.onStateChanged(PlaybackState.Playing) shouldBe Decision.NowPlaying(serverSong)
    }

    @Test
    fun `turning server streams off mid-song drops the play`() {
        planner.onScrobbleServerStreamsChanged(true)
        startPlaying(serverSong)
        listen(99_000)

        planner.onScrobbleServerStreamsChanged(false).shouldBeNull()
        listen(100_000).shouldBeEmpty()
    }

    @Test
    fun `turning server streams off leaves a local song's play alone`() {
        planner.onScrobbleServerStreamsChanged(true)
        startPlaying()
        listen(99_000)

        planner.onScrobbleServerStreamsChanged(false).shouldBeNull()
        listen(1_000) shouldBe listOf(scrobble())
    }

    // Queue changes

    @Test
    fun `the same item with edited song data is still the same play`() {
        startPlaying()
        listen(50_000)
        val editedSong = song.copy(name = "edited")

        changeItem(editedSong, uid = song.id + 10).shouldBeNull()
        positionMs = 50_000
        listen(50_000) shouldBe listOf(scrobble(editedSong))
    }

    @Test
    fun `the same song as a different queue item is a new play`() {
        startPlaying()
        listen(100_000)

        changeItem(song, uid = 99) shouldBe Decision.NowPlaying(song)
        listen(100_000) shouldBe listOf(scrobble(startedAtEpochMs = START_EPOCH_MS + 100_000))
    }

    @Test
    fun `clearing the queue ends the play`() {
        startPlaying()
        listen(99_000)

        changeItem(null).shouldBeNull()
        listen(10_000).shouldBeEmpty()
    }

    private companion object {
        const val START_EPOCH_MS = 1_790_000_000_000L
    }
}
