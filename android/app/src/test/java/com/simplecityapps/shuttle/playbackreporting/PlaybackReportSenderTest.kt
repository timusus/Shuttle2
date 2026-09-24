package com.simplecityapps.shuttle.playbackreporting

import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportPlanner.Call
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackReportSenderTest {
    private val song = createSong(id = 1, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)
    private val session = PlaybackSession(song, "session-1")
    private val playedAt = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private val pendingPlays = PendingPlays(
        RuntimeEnvironment.getApplication().getSharedPreferences("sender_test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
    )
    private val reporter = FakeReporter()
    private var enabled = true

    private fun TestScope.sender() = PlaybackReportSender(
        reporter = reporter,
        pendingPlays = pendingPlays,
        findSongs = { songIds -> listOf(song).filter { it.id in songIds } },
        isEnabled = { enabled },
        now = { playedAt },
        scope = backgroundScope
    )

    // The sender works in backgroundScope, which advanceUntilIdle doesn't wait for. Moving past the
    // timeout runs every queued call, including one that hangs.
    private fun TestScope.settle() {
        advanceTimeBy(PlaybackReportSender.TIMEOUT_MS + 1)
        runCurrent()
    }

    @Test
    fun `calls are sent in order`() = runTest {
        val sender = sender()

        sender.send(listOf(Call.Start(session, 0), Call.Progress(session, 1_000, paused = true), Call.Stop(session, 2_000, playedThrough = false)))
        settle()

        reporter.calls shouldBe listOf("start 0", "progress 1000 true", "stop 2000")
    }

    @Test
    fun `pending plays are not replayed while reporting is off`() = runTest {
        pendingPlays.add(PendingPlays.Play(song.id, playedAt))
        enabled = false
        val sender = sender()

        sender.replayPendingPlays()
        settle()

        reporter.calls.shouldBeEmpty()
        pendingPlays.all() shouldBe listOf(PendingPlays.Play(song.id, playedAt))
    }

    @Test
    fun `a failed play-through is kept, and recorded once a later call succeeds`() = runTest {
        val sender = sender()
        reporter.succeeds = false

        sender.send(listOf(Call.Stop(session, song.duration, playedThrough = true)))
        settle()
        pendingPlays.all() shouldBe listOf(PendingPlays.Play(song.id, playedAt))

        reporter.succeeds = true
        sender.send(listOf(Call.Start(session, 0)))
        settle()

        reporter.calls.last() shouldBe "markPlayed 1 $playedAt"
        pendingPlays.all().shouldBeEmpty()
    }

    @Test
    fun `a failed stop that is not a play-through is dropped`() = runTest {
        val sender = sender()
        reporter.succeeds = false

        sender.send(listOf(Call.Stop(session, 1_000, playedThrough = false)))
        settle()

        pendingPlays.all().shouldBeEmpty()
    }

    @Test
    fun `a call that hangs times out and counts as a failure`() = runTest {
        val sender = sender()
        reporter.hangs = true

        sender.send(listOf(Call.Stop(session, song.duration, playedThrough = true)))
        settle()

        pendingPlays.all() shouldBe listOf(PendingPlays.Play(song.id, playedAt))
    }

    @Test
    fun `pending plays are replayed on request, and a removed song's play is dropped`() = runTest {
        pendingPlays.add(PendingPlays.Play(song.id, playedAt))
        pendingPlays.add(PendingPlays.Play(99, playedAt))
        val sender = sender()

        sender.replayPendingPlays()
        settle()

        reporter.calls shouldBe listOf("markPlayed 1 $playedAt")
        pendingPlays.all().shouldBeEmpty()
    }

    private class FakeReporter : PlaybackReporter {
        val calls = mutableListOf<String>()
        var succeeds = true
        var hangs = false

        override fun handles(song: Song): Boolean = true

        override suspend fun start(
            session: PlaybackSession,
            positionMs: Int
        ): Boolean = record("start $positionMs")

        override suspend fun progress(
            session: PlaybackSession,
            positionMs: Int,
            paused: Boolean
        ): Boolean = record("progress $positionMs $paused")

        override suspend fun stop(
            session: PlaybackSession,
            positionMs: Int
        ): Boolean = record("stop $positionMs")

        override suspend fun markPlayed(
            song: Song,
            playedAt: Instant
        ): Boolean = record("markPlayed ${song.id} $playedAt")

        private suspend fun record(call: String): Boolean {
            calls += call
            if (hangs) awaitCancellation()
            return succeeds
        }
    }
}
