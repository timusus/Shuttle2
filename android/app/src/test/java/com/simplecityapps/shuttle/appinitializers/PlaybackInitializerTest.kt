package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.persistence.QueueStore
import com.simplecityapps.shuttle.playback.RecordPlays
import com.simplecityapps.testing.MainDispatcherRule
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlaybackInitializerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application: Application = RuntimeEnvironment.getApplication()
    private val playbackOperations = FakePlaybackOperations()
    private val songRepository = FakeSongRepository()
    private val queueStore = mockk<QueueStore>(relaxed = true)
    private val startedComponents = mutableListOf<String>()
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val initializer = PlaybackInitializer(
        context = application,
        playbackOperations = playbackOperations,
        recordPlays = RecordPlays(playbackOperations, songRepository, appCoroutineScope, mainDispatcherRule.testDispatcher),
        queueStore = queueStore,
        castStarter = lazy {
            startedComponents += "cast"
            mockk(relaxed = true)
        },
        playRequests = lazy {
            startedComponents += "play requests"
            mockk(relaxed = true)
        },
        bitPerfectOutput = lazy {
            startedComponents += "bit-perfect"
            mockk(relaxed = true)
        },
        queueSongRefresher = lazy {
            startedComponents += "queue song refresher"
            mockk(relaxed = true)
        },
        appCoroutineScope = appCoroutineScope
    )

    @After
    fun tearDown() {
        appCoroutineScope.cancel()
    }

    @Test
    fun `playback starting starts the playback service`() {
        initializer.init(application)

        playbackOperations.playbackStateFlow.value = PlaybackState.Playing

        shadowOf(application).nextStartedService?.component?.className shouldBe PlaybackService::class.java.name
    }

    @Test
    fun `init starts the playback components that run for the life of the app`() {
        startedComponents shouldBe emptyList()

        initializer.init(application)

        startedComponents shouldBe listOf("cast", "play requests", "bit-perfect", "queue song refresher")
    }

    // What gets recorded is RecordPlaysTest's; this is only that init starts it
    @Test
    fun `init starts recording plays`() {
        initializer.init(application)

        playbackOperations.trackEndedFlow.tryEmit(createSong(id = 4, duration = 200_000))

        songRepository.playedThroughSongs.toList() shouldBe listOf(4L)
    }

    @Test
    fun `init restores the saved queue, loading it at the position to resume from`() {
        val load = slot<(Int) -> Unit>()
        every { queueStore.restore(capture(load)) } returns Unit

        initializer.init(application)
        load.captured(30_000)

        playbackOperations.loadedPositions shouldBe listOf(30_000)
    }
}
