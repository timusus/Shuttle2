package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.playbackreporting.PendingPlays
import com.simplecityapps.shuttle.playbackreporting.PlaybackReportSender
import com.simplecityapps.shuttle.settings.SettingsStore
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaybackReportingInitializerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = RuntimeEnvironment.getApplication()
    private val song = createSong(id = 1, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)
    private val playbackManager = FakePlaybackManager()
    private val queueManager = FakeQueueManager()
    private val reporter = FakeReporter()
    private val playbackReporter = AggregatePlaybackReporter(setOf(reporter))
    private val librarySettings = LibrarySettings(
        SettingsStore(
            application.getSharedPreferences("playback_reporting_initializer_test", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        )
    )
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy, so the sender starts its work loop once MainDispatcherRule has set the main dispatcher.
    private val initializer by lazy {
        PlaybackReportingInitializer(
            playbackManager = playbackManager,
            queueManager = queueManager,
            playbackReporter = playbackReporter,
            sender = PlaybackReportSender(
                reporter = playbackReporter,
                pendingPlays = PendingPlays(application.getSharedPreferences("playback_reporting_initializer_test_plays", Context.MODE_PRIVATE)),
                findSongs = { emptyList() },
                isEnabled = { librarySettings.reportPlaybackToServer.value },
                now = { Instant.fromEpochMilliseconds(0) },
                scope = appCoroutineScope
            ),
            librarySettings = librarySettings,
            appCoroutineScope = appCoroutineScope
        )
    }

    private fun playSongAt(positionMs: Int) {
        queueManager.queueStateFlow.value = song.toQueueItem(isCurrent = true).let { item -> QueueState(items = listOf(item), currentItem = item, currentPosition = 0) }
        playbackManager.progressFlow.value = PlaybackProgress(position = positionMs, duration = song.duration)
        playbackManager.playbackStateFlow.value = PlaybackState.Playing
    }

    @After
    fun tearDown() {
        appCoroutineScope.cancel()
    }

    @Test
    fun `a song already playing when reporting attaches starts at its position`() {
        playSongAt(90_000)

        initializer.init(application)

        reporter.calls shouldBe listOf("start 90000")
    }

    @Test
    fun `turning reporting on mid-song starts a play at the current position`() {
        librarySettings.reportPlaybackToServer.value = false
        playSongAt(30_000)
        initializer.init(application)
        reporter.calls.shouldBeEmpty()

        librarySettings.reportPlaybackToServer.value = true

        reporter.calls shouldBe listOf("start 30000")
    }

    @Test
    fun `turning reporting off mid-song stops the play`() {
        playSongAt(30_000)
        initializer.init(application)

        librarySettings.reportPlaybackToServer.value = false

        reporter.calls shouldBe listOf("start 30000", "stop 30000")
    }

    private class FakeReporter : PlaybackReporter {
        val calls = mutableListOf<String>()

        override fun handles(song: Song): Boolean = song.mediaProvider.remote

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
        ): Boolean = record("markPlayed ${song.id}")

        private fun record(call: String): Boolean {
            calls += call
            return true
        }
    }
}
