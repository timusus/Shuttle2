package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.Call
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.State
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test

class PlaybackReportPlannerTest {
    private val remoteSong = createSong(id = 1, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)
    private val otherRemoteSong = createSong(id = 2, duration = 180_000, mediaProvider = MediaProviderType.Jellyfin)
    private val localSong = createSong(id = 3, mediaProvider = MediaProviderType.Shuttle)

    private var sessionCount = 0
    private val planner = PlaybackReportPlanner(
        isReportable = { song -> song.mediaProvider.remote },
        newSessionId = { "session-${++sessionCount}" }
    ).apply { onEnabledChanged(enabled = true, nowMs = 0) }

    private val firstSession = PlaybackSession(remoteSong, "session-1")

    private fun playRemoteSong(): List<Call> {
        planner.onCurrentItemChanged(uid = 10, song = remoteSong, nowMs = 0)
        return planner.onStateChanged(State.Playing, nowMs = 0)
    }

    @Test
    fun `playing a remote song starts a play at its position`() {
        playRemoteSong() shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `a restored song that is only loaded is not reported`() {
        planner.onCurrentItemChanged(uid = 10, song = remoteSong, nowMs = 0).shouldBeEmpty()
        planner.onStateChanged(State.Paused, nowMs = 0).shouldBeEmpty()
        planner.onProgress(positionMs = 60_000, nowMs = 100).shouldBeEmpty()
    }

    @Test
    fun `resuming a restored song starts at the restored position`() {
        planner.onCurrentItemChanged(uid = 10, song = remoteSong, nowMs = 0)
        planner.onProgress(positionMs = 60_000, nowMs = 0)

        planner.onStateChanged(State.Playing, nowMs = 1_000) shouldBe listOf(Call.Start(firstSession, 60_000))
    }

    @Test
    fun `a local song is not reported`() {
        planner.onCurrentItemChanged(uid = 10, song = localSong, nowMs = 0)

        planner.onStateChanged(State.Playing, nowMs = 0).shouldBeEmpty()
        planner.onProgress(positionMs = 20_000, nowMs = 20_000).shouldBeEmpty()
    }

    @Test
    fun `a track change stops the previous play at its last position and starts the next`() {
        playRemoteSong()
        planner.onProgress(positionMs = 5_000, nowMs = 5_000)

        planner.onCurrentItemChanged(uid = 11, song = otherRemoteSong, nowMs = 5_100) shouldBe
            listOf(
                Call.Stop(firstSession, 5_000, playedThrough = false),
                Call.Start(PlaybackSession(otherRemoteSong, "session-2"), 0)
            )
    }

    @Test
    fun `changing to a local song only stops the remote play`() {
        playRemoteSong()
        planner.onProgress(positionMs = 5_000, nowMs = 5_000)

        planner.onCurrentItemChanged(uid = 11, song = localSong, nowMs = 5_100) shouldBe
            listOf(Call.Stop(firstSession, 5_000, playedThrough = false))
    }

    @Test
    fun `the same item with edited song data is still the same play`() {
        playRemoteSong()

        planner.onCurrentItemChanged(uid = 10, song = remoteSong.copy(name = "edited"), nowMs = 100).shouldBeEmpty()
    }

    @Test
    fun `pause and resume report progress straight away`() {
        playRemoteSong()
        planner.onProgress(positionMs = 3_000, nowMs = 3_000)

        planner.onStateChanged(State.Paused, nowMs = 3_000) shouldBe listOf(Call.Progress(firstSession, 3_000, paused = true))
        planner.onStateChanged(State.Playing, nowMs = 60_000) shouldBe listOf(Call.Progress(firstSession, 3_000, paused = false))
    }

    @Test
    fun `loading between pause and play reports nothing extra`() {
        playRemoteSong()

        planner.onStateChanged(State.Loading, nowMs = 1_000).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 2_000).shouldBeEmpty()
    }

    @Test
    fun `progress is reported every 10 seconds while playing, not on every tick`() {
        playRemoteSong()

        (1..99).flatMap { tick -> planner.onProgress(positionMs = tick * 100, nowMs = tick * 100L) }.shouldBeEmpty()
        planner.onProgress(positionMs = 10_000, nowMs = 10_000) shouldBe listOf(Call.Progress(firstSession, 10_000, paused = false))
        planner.onProgress(positionMs = 10_100, nowMs = 10_100).shouldBeEmpty()
    }

    @Test
    fun `no progress is reported on a timer while paused`() {
        playRemoteSong()
        planner.onStateChanged(State.Paused, nowMs = 0)

        planner.onProgress(positionMs = 0, nowMs = 30_000).shouldBeEmpty()
    }

    @Test
    fun `a seek reports progress straight away`() {
        playRemoteSong()
        planner.onProgress(positionMs = 1_000, nowMs = 1_000)

        planner.onProgress(positionMs = 90_000, nowMs = 1_100) shouldBe listOf(Call.Progress(firstSession, 90_000, paused = false))
    }

    @Test
    fun `a seek while paused reports paused progress`() {
        playRemoteSong()
        planner.onProgress(positionMs = 1_000, nowMs = 1_000)
        planner.onStateChanged(State.Paused, nowMs = 1_000)

        planner.onProgress(positionMs = 30_000, nowMs = 20_000) shouldBe listOf(Call.Progress(firstSession, 30_000, paused = true))
    }

    @Test
    fun `resuming after a long pause is not mistaken for a seek`() {
        playRemoteSong()
        planner.onProgress(positionMs = 1_000, nowMs = 1_000)
        planner.onStateChanged(State.Paused, nowMs = 1_000)
        planner.onStateChanged(State.Playing, nowMs = 60_000)

        planner.onProgress(positionMs = 1_100, nowMs = 60_100).shouldBeEmpty()
    }

    @Test
    fun `clearing the queue stops the play`() {
        playRemoteSong()
        planner.onProgress(positionMs = 4_000, nowMs = 4_000)

        planner.onCurrentItemChanged(uid = null, song = null, nowMs = 4_100) shouldBe listOf(Call.Stop(firstSession, 4_000, playedThrough = false))
    }

    @Test
    fun `a track that plays through stops at its duration`() {
        playRemoteSong()

        planner.onTrackEnded(remoteSong) shouldBe listOf(Call.Stop(firstSession, remoteSong.duration, playedThrough = true))
        // The queue then moves on: the ended play isn't stopped twice.
        planner.onCurrentItemChanged(uid = 11, song = otherRemoteSong, nowMs = 200_000) shouldBe
            listOf(Call.Start(PlaybackSession(otherRemoteSong, "session-2"), 0))
    }

    @Test
    fun `a late tick after a track ends does not start it again, a repeat does`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        planner.onProgress(positionMs = 200_000, nowMs = 200_000).shouldBeEmpty()
        planner.onProgress(positionMs = 100, nowMs = 200_100) shouldBe listOf(Call.Start(PlaybackSession(remoteSong, "session-2"), 100))
    }

    @Test
    fun `a track end for a song that is not being reported is ignored`() {
        planner.onTrackEnded(remoteSong).shouldBeEmpty()
    }

    @Test
    fun `nothing is reported while reporting is off`() {
        planner.onEnabledChanged(enabled = false, nowMs = 0)

        playRemoteSong().shouldBeEmpty()
        planner.onProgress(positionMs = 20_000, nowMs = 20_000).shouldBeEmpty()
        planner.onTrackEnded(remoteSong).shouldBeEmpty()
        planner.onCurrentItemChanged(uid = 11, song = otherRemoteSong, nowMs = 20_100).shouldBeEmpty()
    }

    @Test
    fun `turning reporting on mid-song starts a new play at the current position`() {
        planner.onEnabledChanged(enabled = false, nowMs = 0)
        playRemoteSong()
        planner.onProgress(positionMs = 30_000, nowMs = 30_000)

        planner.onEnabledChanged(enabled = true, nowMs = 30_100) shouldBe listOf(Call.Start(firstSession, 30_000))
    }

    @Test
    fun `turning reporting off mid-song stops the play, and turning it back on starts a new one`() {
        playRemoteSong()
        planner.onProgress(positionMs = 30_000, nowMs = 30_000)

        planner.onEnabledChanged(enabled = false, nowMs = 30_100) shouldBe listOf(Call.Stop(firstSession, 30_000, playedThrough = false))
        planner.onProgress(positionMs = 50_000, nowMs = 50_000).shouldBeEmpty()
        planner.onEnabledChanged(enabled = true, nowMs = 50_100) shouldBe listOf(Call.Start(PlaybackSession(remoteSong, "session-2"), 50_000))
    }
}
