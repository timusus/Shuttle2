package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.persistence.resumePosition
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.settings.PlaybackSettings
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.Preference
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * iOS keeps the queue, the modes, the position and the speed across launches: each test plays on one "launch" (a
 * controller and store over a fake engine), then starts another over the same prefs, as the app does at launch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlaybackStoreTest {
    private val prefs = InMemoryKeyValueStore()

    private val a = song(1)
    private val b = song(2)
    private val c = song(3)
    private val d = song(4)

    private val songRepository = FakeSongRepository().apply { setSongs(listOf(a, b, c, d)) }

    private class Launch(
        val engine: FakeIosAudioPlayer,
        val controller: IosPlayerController,
        val manager: PlaybackPreferenceManager,
        val speed: Preference<Float>
    )

    /** Starts the app's playback over [prefs]: the controller, and the store that restores into it. */
    private fun TestScope.launch(): Launch {
        val engine = FakeIosAudioPlayer()
        val manager = PlaybackPreferenceManager(prefs)
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val controller = IosPlayerController(
            player = engine,
            resolver = { song, _ -> IosStream("song:${song.id}") },
            scope = scope,
            random = Random(1),
            resumePosition = manager::resumePosition
        )
        val speed = Preference(prefs, PlaybackSettings.PlaybackSpeed)
        IosPlaybackStore(controller, manager, speed, songRepository, scope, readContext = UnconfinedTestDispatcher(testScheduler)).start()
        engine.settle()
        return Launch(engine, controller, manager, speed)
    }

    private suspend fun Launch.play(
        songs: List<Song>,
        position: Int = 0
    ) {
        controller.queueOperations.setQueue(songs, null, position)
        controller.load { }
        engine.settle()
        controller.play()
        engine.settle()
    }

    @Test
    fun `nothing saved restores an empty queue that counts as restored`() = runTest {
        val launch = launch()

        launch.controller.queueOperations.queueStateFlow.value.items shouldBe emptyList()
        launch.controller.queueOperations.hasRestoredQueue shouldBe true
        launch.manager.queueIds.shouldBeNull()
    }

    @Test
    fun `the queue and the position in it are restored paused at the saved position`() = runTest {
        val first = launch()
        first.play(listOf(a, b, c), position = 1)
        first.engine.tick(5_000)
        first.controller.pause()
        first.engine.settle()

        val second = launch()

        val queue = second.controller.queueOperations
        queue.queueStateFlow.value.items.map { it.song } shouldBe listOf(a, b, c)
        queue.getCurrentPosition() shouldBe 1
        queue.hasRestoredQueue shouldBe true
        second.controller.playbackState() shouldBe PlaybackState.Paused
        second.controller.getProgress() shouldBe 5_000
        // Loaded paused, not played.
        second.engine.calls.first() shouldBe "load song:2@5000"
    }

    @Test
    fun `the position saved while playing moves on each second of playback`() = runTest {
        val first = launch()
        first.play(listOf(a, b))
        first.engine.tick(400)
        first.manager.playbackPosition shouldBe 0
        first.engine.tick(1_200)
        first.manager.playbackPosition shouldBe 1_200
        first.controller.seekTo(90_000)
        first.manager.playbackPosition shouldBe 90_000
    }

    @Test
    fun `shuffle and repeat and the shuffled order are restored`() = runTest {
        val first = launch()
        first.play(listOf(a, b, c, d))
        first.controller.queueOperations.setShuffleMode(ShuffleMode.On, reshuffle = true)
        first.controller.queueOperations.setRepeatMode(RepeatMode.One)
        val shuffled = first.controller.queueOperations.queueStateFlow.value.items.map { it.song }
        val current = first.controller.queueOperations.getCurrentItem()?.song

        val second = launch()

        val queue = second.controller.queueOperations
        queue.getShuffleMode() shouldBe ShuffleMode.On
        queue.getRepeatMode() shouldBe RepeatMode.One
        queue.queueStateFlow.value.items.map { it.song } shouldBe shuffled
        queue.getCurrentItem()?.song shouldBe current
    }

    @Test
    fun `the speed is restored`() = runTest {
        val first = launch()
        first.controller.setPlaybackSpeed(1.5f)
        first.speed.value shouldBe 1.5f

        val second = launch()

        second.controller.getPlaybackSpeed() shouldBe 1.5f
        second.engine.calls.contains("speed 1.5") shouldBe true
    }

    @Test
    fun `skipping to another song clears the saved position until one is saved for it`() = runTest {
        val first = launch()
        first.play(listOf(a, b))
        first.engine.tick(30_000)
        first.manager.playbackPosition shouldBe 30_000

        first.controller.queueOperations.setCurrentItem(first.controller.queueOperations.getQueue()[1])
        first.engine.settle()

        first.manager.playbackPosition shouldBe 0
        first.manager.queuePosition shouldBe 1
        first.manager.nowPlaying?.songId shouldBe b.id
    }

    @Test
    fun `a song gone from the library is dropped from the restored queue`() = runTest {
        val first = launch()
        first.play(listOf(a, b, c), position = 2)
        songRepository.setSongs(listOf(a, c))

        val second = launch()

        second.controller.queueOperations.queueStateFlow.value.items.map { it.song } shouldBe listOf(a, c)
        second.controller.queueOperations.getCurrentPosition() shouldBe 1
    }

    @Test
    fun `a saved queue none of whose songs are left restores nothing and forgets the saved song`() = runTest {
        val first = launch()
        first.play(listOf(a))
        songRepository.setSongs(emptyList())

        val second = launch()

        second.controller.queueOperations.queueStateFlow.value.items shouldBe emptyList()
        second.controller.queueOperations.hasRestoredQueue shouldBe true
        second.manager.nowPlaying.shouldBeNull()
    }
}
