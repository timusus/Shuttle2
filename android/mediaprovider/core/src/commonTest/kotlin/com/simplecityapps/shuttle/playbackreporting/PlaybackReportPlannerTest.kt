package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.playback.Play
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.Call
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.State
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class PlaybackReportPlannerTest {
    private val remoteSong = createSong(id = 1, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)
    private val otherRemoteSong = createSong(id = 2, duration = 180_000, mediaProvider = MediaProviderType.Jellyfin)
    private val localSong = createSong(id = 3, mediaProvider = MediaProviderType.Shuttle)

    private val planner = PlaybackReportPlanner(isReportable = { song -> song.mediaProvider.remote })
        .apply { onEnabledChanged(enabled = true, nowMs = 0) }

    private val firstSession = PlaybackSession(remoteSong, "play-10")

    /** Makes queue item [uid] current, with the player's play of it, as the player publishes both. */
    private fun current(
        uid: Long?,
        song: Song?,
        nowMs: Long
    ): List<Call> = planner.onCurrentItemChanged(uid, song, nowMs) + planner.onPlayChanged(uid?.let { Play("play-$it", it) }, nowMs)

    private fun playRemoteSong(): List<Call> {
        current(uid = 10, song = remoteSong, nowMs = 0)
        return planner.onStateChanged(State.Playing, nowMs = 0)
    }

    @Test
    fun `a play takes the player's play id whichever of the item and its play arrives first`() {
        planner.onStateChanged(State.Playing, nowMs = 0)

        planner.onCurrentItemChanged(uid = 10, song = remoteSong, nowMs = 0).shouldBeEmpty()
        planner.onPlayChanged(Play("stream-a", 10), nowMs = 0) shouldBe listOf(Call.Start(PlaybackSession(remoteSong, "stream-a"), 0))

        planner.onPlayChanged(Play("stream-b", 11), nowMs = 100).shouldBeEmpty()
        planner.onCurrentItemChanged(uid = 11, song = otherRemoteSong, nowMs = 100) shouldBe
            listOf(Call.Stop(PlaybackSession(remoteSong, "stream-a"), 0, playedThrough = false), Call.Start(PlaybackSession(otherRemoteSong, "stream-b"), 0))
    }

    @Test
    fun `a play of the previous item is not used for the new one`() {
        playRemoteSong()

        planner.onCurrentItemChanged(uid = 11, song = otherRemoteSong, nowMs = 100) shouldBe listOf(Call.Stop(firstSession, 0, playedThrough = false))
    }

    @Test
    fun `the same song queued twice gets a play id for each copy`() {
        playRemoteSong()

        current(uid = 11, song = remoteSong, nowMs = 100) shouldBe
            listOf(Call.Stop(firstSession, 0, playedThrough = false), Call.Start(PlaybackSession(remoteSong, "play-11"), 0))
    }

    @Test
    fun `a new play of the current item stops the old one and starts the new one from its start`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        // A repeat's next loop on iOS opens a fresh stream under a new play id.
        planner.onPlayChanged(Play("loop-2", 10), nowMs = 200_000) shouldBe listOf(Call.Start(PlaybackSession(remoteSong, "loop-2"), 0))
        planner.onProgress(positionMs = 30_000, nowMs = 230_000)
        planner.onPlayChanged(Play("loop-3", 10), nowMs = 230_100) shouldBe
            listOf(Call.Stop(PlaybackSession(remoteSong, "loop-2"), 30_000, playedThrough = false), Call.Start(PlaybackSession(remoteSong, "loop-3"), 0))
    }

    @Test
    fun `playing a remote song starts a play at its position`() {
        playRemoteSong() shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `a restored song that is only loaded is not reported`() {
        current(uid = 10, song = remoteSong, nowMs = 0).shouldBeEmpty()
        planner.onStateChanged(State.Paused, nowMs = 0).shouldBeEmpty()
        planner.onProgress(positionMs = 60_000, nowMs = 100).shouldBeEmpty()
    }

    @Test
    fun `resuming a restored song starts at the restored position`() {
        current(uid = 10, song = remoteSong, nowMs = 0)
        planner.onProgress(positionMs = 60_000, nowMs = 0)

        planner.onStateChanged(State.Playing, nowMs = 1_000) shouldBe listOf(Call.Start(firstSession, 60_000))
    }

    @Test
    fun `a local song is not reported`() {
        current(uid = 10, song = localSong, nowMs = 0)

        planner.onStateChanged(State.Playing, nowMs = 0).shouldBeEmpty()
        planner.onProgress(positionMs = 20_000, nowMs = 20_000).shouldBeEmpty()
    }

    @Test
    fun `a track change stops the previous play at its last position and starts the next`() {
        playRemoteSong()
        planner.onProgress(positionMs = 5_000, nowMs = 5_000)

        current(uid = 11, song = otherRemoteSong, nowMs = 5_100) shouldBe
            listOf(
                Call.Stop(firstSession, 5_000, playedThrough = false),
                Call.Start(PlaybackSession(otherRemoteSong, "play-11"), 0)
            )
    }

    @Test
    fun `changing to a local song only stops the remote play`() {
        playRemoteSong()
        planner.onProgress(positionMs = 5_000, nowMs = 5_000)

        current(uid = 11, song = localSong, nowMs = 5_100) shouldBe
            listOf(Call.Stop(firstSession, 5_000, playedThrough = false))
    }

    @Test
    fun `the same item with edited song data is still the same play`() {
        playRemoteSong()

        current(uid = 10, song = remoteSong.copy(name = "edited"), nowMs = 100).shouldBeEmpty()
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
    fun `progress is reported every 10 seconds while playing not on every tick`() {
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

        current(uid = null, song = null, nowMs = 4_100) shouldBe listOf(Call.Stop(firstSession, 4_000, playedThrough = false))
    }

    @Test
    fun `a track that plays through stops at its duration`() {
        playRemoteSong()

        planner.onTrackEnded(remoteSong) shouldBe listOf(Call.Stop(firstSession, remoteSong.duration, playedThrough = true))
        // The queue then moves on: the ended play isn't stopped twice.
        current(uid = 11, song = otherRemoteSong, nowMs = 200_000) shouldBe
            listOf(Call.Start(PlaybackSession(otherRemoteSong, "play-11"), 0))
    }

    @Test
    fun `a late tick after a track ends does not start it again a repeat does`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        planner.onProgress(positionMs = 200_000, nowMs = 200_000).shouldBeEmpty()
        // The reset tick could still be the next item's, so a repeat starts once the position has stayed back a while,
        // from where it went back to.
        planner.onProgress(positionMs = 100, nowMs = 200_100).shouldBeEmpty()
        planner.onProgress(positionMs = 600, nowMs = 200_600).shouldBeEmpty()
        planner.onProgress(positionMs = 1_100, nowMs = 201_100) shouldBe listOf(Call.Start(firstSession, 100))
    }

    @Test
    fun `a repeat whose reset tick comes before its track end still starts`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onProgress(positionMs = 0, nowMs = 200_000) shouldBe listOf(Call.Progress(firstSession, 0, paused = false))
        planner.onTrackEnded(remoteSong) shouldBe listOf(Call.Stop(firstSession, remoteSong.duration, playedThrough = true))

        planner.onProgress(positionMs = 500, nowMs = 200_500).shouldBeEmpty()
        planner.onProgress(positionMs = 1_000, nowMs = 201_000) shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `a late tick slightly behind the end does not start the ended song`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        planner.onProgress(positionMs = 200_000, nowMs = 200_000).shouldBeEmpty()
        planner.onProgress(positionMs = 199_950, nowMs = 200_100).shouldBeEmpty()
        planner.onProgress(positionMs = 200_000, nowMs = 201_500).shouldBeEmpty()
    }

    @Test
    fun `several ticks of the next item before its item change do not start the ended song`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        planner.onProgress(positionMs = 0, nowMs = 200_000).shouldBeEmpty()
        planner.onProgress(positionMs = 5, nowMs = 200_005).shouldBeEmpty()
        planner.onProgress(positionMs = 100, nowMs = 200_100).shouldBeEmpty()
        current(uid = 11, song = otherRemoteSong, nowMs = 200_110) shouldBe
            listOf(Call.Start(PlaybackSession(otherRemoteSong, "play-11"), 0))
    }

    @Test
    fun `a playing state change between a track end and the next item change does not start the ended song`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)

        planner.onStateChanged(State.Loading, nowMs = 200_000).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 200_001).shouldBeEmpty()
        planner.onProgress(positionMs = 0, nowMs = 200_002).shouldBeEmpty()
        planner.onStateChanged(State.Loading, nowMs = 200_003).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 200_004).shouldBeEmpty()
        current(uid = 11, song = otherRemoteSong, nowMs = 200_010) shouldBe
            listOf(Call.Start(PlaybackSession(otherRemoteSong, "play-11"), 0))
    }

    @Test
    fun `seeking to the start after a track ends while playing starts it again from there`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)
        planner.onProgress(positionMs = 200_000, nowMs = 200_000)

        planner.onProgress(positionMs = 0, nowMs = 205_000).shouldBeEmpty()
        planner.onProgress(positionMs = 1_000, nowMs = 206_000) shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `seeking to the start after the queue plays out then playing starts it again from there`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)
        planner.onStateChanged(State.Paused, nowMs = 200_000).shouldBeEmpty()

        planner.onProgress(positionMs = 0, nowMs = 210_000).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 215_000) shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `seeking back after the queue plays out then playing starts it again from the position played from`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)
        planner.onStateChanged(State.Paused, nowMs = 200_000)

        planner.onProgress(positionMs = 0, nowMs = 210_000).shouldBeEmpty()
        planner.onProgress(positionMs = 30_000, nowMs = 211_000).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 215_000) shouldBe listOf(Call.Start(firstSession, 30_000))
    }

    @Test
    fun `playing after the queue plays out starts again from the start not the stale end`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)
        planner.onStateChanged(State.Paused, nowMs = 200_000)
        planner.onProgress(positionMs = 200_000, nowMs = 200_001)

        // Play seeks back to the start; its state change can arrive before the position does.
        planner.onStateChanged(State.Playing, nowMs = 300_000).shouldBeEmpty()
        planner.onProgress(positionMs = 0, nowMs = 300_005).shouldBeEmpty()
        planner.onProgress(positionMs = 500, nowMs = 300_505).shouldBeEmpty()
        planner.onProgress(positionMs = 1_000, nowMs = 301_005) shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `playing after the queue plays out with the position first starts again from the start`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong)
        planner.onStateChanged(State.Paused, nowMs = 200_000)

        planner.onProgress(positionMs = 0, nowMs = 300_000).shouldBeEmpty()
        planner.onStateChanged(State.Playing, nowMs = 300_005).shouldBeEmpty()
        planner.onProgress(positionMs = 1_000, nowMs = 301_005) shouldBe listOf(Call.Start(firstSession, 0))
    }

    @Test
    fun `auto-advance with the next item's progress before its item change starts the ended song only once the next one`() {
        playRemoteSong()
        planner.onProgress(positionMs = 199_900, nowMs = 199_900)
        planner.onTrackEnded(remoteSong) shouldBe listOf(Call.Stop(firstSession, remoteSong.duration, playedThrough = true))

        planner.onProgress(positionMs = 0, nowMs = 200_000).shouldBeEmpty()
        current(uid = 11, song = otherRemoteSong, nowMs = 200_010) shouldBe
            listOf(Call.Start(PlaybackSession(otherRemoteSong, "play-11"), 0))
        planner.onProgress(positionMs = 500, nowMs = 200_510).shouldBeEmpty()
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
        current(uid = 11, song = otherRemoteSong, nowMs = 20_100).shouldBeEmpty()
    }

    @Test
    fun `turning reporting on mid-song starts a new play at the current position`() {
        planner.onEnabledChanged(enabled = false, nowMs = 0)
        playRemoteSong()
        planner.onProgress(positionMs = 30_000, nowMs = 30_000)

        planner.onEnabledChanged(enabled = true, nowMs = 30_100) shouldBe listOf(Call.Start(firstSession, 30_000))
    }

    @Test
    fun `turning reporting off mid-song stops the play and turning it back on starts a new one`() {
        playRemoteSong()
        planner.onProgress(positionMs = 30_000, nowMs = 30_000)

        planner.onEnabledChanged(enabled = false, nowMs = 30_100) shouldBe listOf(Call.Stop(firstSession, 30_000, playedThrough = false))
        planner.onProgress(positionMs = 50_000, nowMs = 50_000).shouldBeEmpty()
        planner.onEnabledChanged(enabled = true, nowMs = 50_100) shouldBe listOf(Call.Start(firstSession, 50_000))
    }
}
