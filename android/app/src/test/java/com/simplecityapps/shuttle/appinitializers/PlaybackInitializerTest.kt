package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PositionAnchor
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.testing.MainDispatcherRule
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.setMain
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
    private val queueOperations = FakeQueueOperations()
    private val startedComponents = mutableListOf<String>()
    private val preferences = PlaybackPreferenceManager(
        application.getSharedPreferences("playback_initializer_test", Context.MODE_PRIVATE),
        Moshi.Builder().build()
    )
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val initializer = createInitializer(songRepository, appCoroutineScope)

    private fun createInitializer(
        songRepository: SongRepository,
        appCoroutineScope: CoroutineScope
    ) = PlaybackInitializer(
        context = application,
        songRepository = songRepository,
        playbackOperations = playbackOperations,
        queueOperations = queueOperations,
        playbackPreferenceManager = preferences,
        castStarter = Lazy {
            startedComponents += "cast"
            mockk(relaxed = true)
        },
        playRequests = Lazy {
            startedComponents += "play requests"
            mockk(relaxed = true)
        },
        bitPerfectOutput = Lazy {
            startedComponents += "bit-perfect"
            mockk(relaxed = true)
        },
        appCoroutineScope = appCoroutineScope
    )

    private val songs = listOf(createSong(id = 1), createSong(id = 2), createSong(id = 3))

    @After
    fun tearDown() {
        appCoroutineScope.cancel()
    }

    @Test
    fun `the state held when collection starts is not saved over the preferences`() {
        preferences.queueIds = "7,8"
        preferences.shuffleMode = ShuffleMode.On
        preferences.repeatMode = RepeatMode.All
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
        playbackOperations.progressFlow.value = PlaybackProgress(position = 90_000, duration = 200_000)

        initializer.init(application)

        preferences.queueIds shouldBe "7,8"
        preferences.shuffleMode shouldBe ShuffleMode.On
        preferences.repeatMode shouldBe RepeatMode.All
        preferences.playbackPosition shouldBe null
        shadowOf(application).nextStartedService shouldBe null
    }

    @Test
    fun `a queue change saves the queue and position`() {
        initializer.init(application)

        publishQueue(currentPosition = 1, contentVersion = 1)

        preferences.queueIds shouldBe "1,2,3"
        preferences.shuffleQueueIds shouldBe "1,2,3"
        preferences.queuePosition shouldBe 1
    }

    @Test
    fun `a position change saves the position but not the queue`() {
        initializer.init(application)
        publishQueue(currentPosition = 0, contentVersion = 1)
        preferences.queueIds = "7,8"

        publishQueue(currentPosition = 2, contentVersion = 1)

        preferences.queuePosition shouldBe 2
        preferences.queueIds shouldBe "7,8"
    }

    @Test
    fun `shuffle and repeat changes are saved`() {
        initializer.init(application)

        queueOperations.shuffleModeFlow.value = ShuffleMode.On
        queueOperations.repeatModeFlow.value = RepeatMode.One

        preferences.shuffleMode shouldBe ShuffleMode.On
        preferences.repeatMode shouldBe RepeatMode.One
    }

    @Test
    fun `playback starting starts the playback service`() {
        initializer.init(application)

        playbackOperations.playbackStateFlow.value = PlaybackState.Playing

        shadowOf(application).nextStartedService?.component?.className shouldBe PlaybackService::class.java.name
    }

    @Test
    fun `progress is saved immediately, then throttled to once it has moved by more than a second`() {
        initializer.init(application)

        // The first observed progress is the initial anchor: no prior save to compare against, so it
        // writes straight away rather than waiting for a second tick to establish a baseline.
        playbackOperations.progressFlow.value = PlaybackProgress(position = 5_000, duration = 200_000)
        preferences.playbackPosition shouldBe 5_000

        playbackOperations.progressFlow.value = PlaybackProgress(position = 5_900, duration = 200_000)
        preferences.playbackPosition shouldBe 5_000

        playbackOperations.progressFlow.value = PlaybackProgress(position = 6_500, duration = 200_000)
        preferences.playbackPosition shouldBe 6_500
    }

    @Test
    fun `sub-second progress ticks in either direction do not write to preferences`() {
        initializer.init(application)
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // Small forward and backward ticks stay under the throttle: no further writes.
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_400, duration = 200_000)
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_100, duration = 200_000)
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_700, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000
    }

    @Test
    fun `restarting a track saves the reset position, so a force-stop restores from 0`() {
        initializer.init(application)

        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // Restarting the track seeks back to 0 - a discontinuity, so it's saved immediately, rather
        // than being masked by the old (higher) high-water mark. A restore after a force-stop reads
        // this value straight back as the seek position.
        playbackOperations.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 0, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 0
    }

    @Test
    fun `seeking backwards saves the earlier position immediately`() {
        initializer.init(application)

        playbackOperations.progressFlow.value = PlaybackProgress(position = 90_000, duration = 200_000)
        preferences.playbackPosition shouldBe 90_000

        playbackOperations.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 30_000, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 30_000
    }

    @Test
    fun `a track change saves the new track's position immediately, even under the throttle`() {
        initializer.init(application)

        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // The new track starts close to the old saved position - under the throttle, but a track
        // change is a discontinuity, so it's still saved immediately.
        playbackOperations.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 65_200, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 65_200
    }

    @Test
    fun `init starts the playback components that run for the life of the app`() {
        startedComponents shouldBe emptyList()

        initializer.init(application)

        startedComponents shouldBe listOf("cast", "play requests", "bit-perfect")
    }

    @Test
    fun `a cleared saved position lets the next progress be saved straight away`() {
        initializer.init(application)
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // The playback manager clears it on a pause with no position.
        preferences.playbackPosition = null

        // Under a second from the cleared position: with no saved position there's nothing to throttle against.
        playbackOperations.progressFlow.value = PlaybackProgress(position = 65_500, duration = 200_000)
        preferences.playbackPosition shouldBe 65_500
    }

    @Test
    fun `progress is throttled against the saved position, even one the playback manager saved`() {
        initializer.init(application)
        playbackOperations.progressFlow.value = PlaybackProgress(position = 195_000, duration = 200_000)
        preferences.playbackPosition shouldBe 195_000

        // A track end resets the saved position to 0 for the next track.
        preferences.playbackPosition = 0

        playbackOperations.progressFlow.value = PlaybackProgress(position = 400, duration = 200_000)
        preferences.playbackPosition shouldBe 0

        playbackOperations.progressFlow.value = PlaybackProgress(position = 1_200, duration = 200_000)
        preferences.playbackPosition shouldBe 1_200
    }

    @Test
    fun `a track end records the song as played through`() {
        initializer.init(application)
        val endedSong = createSong(id = 4, duration = 200_000)

        playbackOperations.trackEndedFlow.tryEmit(endedSong)

        awaitUntil { songRepository.playCountIncrements.isNotEmpty() }
        songRepository.playbackPositions.toList() shouldBe listOf(4L to 200_000)
        songRepository.playCountIncrements.toList() shouldBe listOf(4L)
    }

    @Test
    fun `a pause records the song's position`() {
        initializer.init(application)
        val pausedSong = createSong(id = 5, duration = 200_000)

        playbackOperations.pausePositionFlow.tryEmit(SongPosition(pausedSong, 42_000))

        awaitUntil { songRepository.playbackPositions.isNotEmpty() }
        songRepository.playbackPositions.toList() shouldBe listOf(5L to 42_000)
        songRepository.playCountIncrements.toList() shouldBe emptyList()
    }

    @Test
    fun `songs not in the library are left out of the saved queue, and the position is found among the rest`() {
        initializer.init(application)

        publishQueue(listOf(createSong(id = 1), createSong(id = -5), createSong(id = 2), createSong(id = 3)), currentPosition = 2, contentVersion = 1)

        preferences.queueIds shouldBe "1,2,3"
        preferences.shuffleQueueIds shouldBe "1,2,3"
        preferences.queuePosition shouldBe 1
        preferences.restoreQueuePositionFromStart shouldBe false
    }

    @Test
    fun `while an opened file plays, the saved position is the library song after it, from the start`() {
        initializer.init(application)

        publishQueue(listOf(createSong(id = 1), createSong(id = -5), createSong(id = 2)), currentPosition = 1, contentVersion = 1)
        preferences.queuePosition shouldBe 1
        preferences.restoreQueuePositionFromStart shouldBe true

        // With no library song after it, the one before.
        publishQueue(listOf(createSong(id = 1), createSong(id = -5)), currentPosition = 1, contentVersion = 2)
        preferences.queueIds shouldBe "1"
        preferences.queuePosition shouldBe 0
        preferences.restoreQueuePositionFromStart shouldBe true

        // On its own, there's nothing to save.
        publishQueue(listOf(createSong(id = -5)), currentPosition = 0, contentVersion = 3)
        preferences.queueIds shouldBe null
        preferences.queuePosition shouldBe null
    }

    @Test
    fun `the saved song is saved with the position, and read back with where it resumes from`() {
        initializer.init(application)

        publishQueue(currentPosition = 1, contentVersion = 1)
        preferences.playbackPosition = 30_000

        preferences.nowPlaying?.songId shouldBe 2L
        preferences.nowPlaying?.title shouldBe songs[1].name
        preferences.nowPlaying?.positionMs shouldBe 30_000

        publishQueue(currentPosition = 2, contentVersion = 1)
        preferences.nowPlaying?.songId shouldBe 3L
    }

    @Test
    fun `while an opened file plays, the saved song is the library song after it, from the start`() {
        initializer.init(application)
        preferences.playbackPosition = 30_000

        publishQueue(listOf(createSong(id = 1), createSong(id = -5), createSong(id = 2)), currentPosition = 1, contentVersion = 1)

        preferences.nowPlaying?.songId shouldBe 2L
        preferences.nowPlaying?.positionMs shouldBe 0
    }

    @Test
    fun `an emptied queue clears the saved song`() {
        initializer.init(application)
        publishQueue(currentPosition = 0, contentVersion = 1)

        publishQueue(listOf(createSong(id = -5)), currentPosition = 0, contentVersion = 2)

        preferences.nowPlaying shouldBe null
    }

    @Test
    fun `a restore that brings nothing back clears the saved song`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(emptyList())
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.nowPlaying = NowPlayingSnapshot.of(songs[1])

        initializer.init(application)

        awaitUntil { queueOperations.hasRestoredQueue }
        awaitUntil { preferences.nowPlaying == null }
    }

    @Test
    fun `a restore drops songs no longer in the library and keeps the position on the saved song`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 3), createSong(id = 4)))
        preferences.queueIds = "1,2,3,1,4"
        preferences.queuePosition = 4
        preferences.playbackPosition = 30_000

        initializer.init(application)

        awaitUntil { queueOperations.hasRestoredQueue }
        queueOperations.lastSetQueue?.map { song -> song.id } shouldBe listOf(1L, 3L, 1L, 4L)
        queueOperations.lastSetQueuePosition shouldBe 3
        playbackOperations.loadedPositions shouldBe listOf(30_000)
    }

    @Test
    fun `a restore that brings back every saved song doesn't save the queue again, and a change after it is saved`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(songs)
        queueOperations.publishesRestoredQueue = true
        preferences.queueIds = "1,2,3"
        preferences.shuffleQueueIds = "1,2,3"
        preferences.queuePosition = 1

        val main = initAndRestore()

        preferences.nowPlaying?.songId shouldBe 2L
        queueOperations.shuffleModeQueueReads shouldBe 0
        preferences.queuePosition shouldBe 1

        publishQueue(listOf(createSong(id = 3), createSong(id = 1)), currentPosition = 0, contentVersion = queueOperations.queueStateFlow.value.contentVersion + 1)
        main.runUntilIdle()
        preferences.queueIds shouldBe "3,1"
    }

    @Test
    fun `a restore that drops songs saves the queue that's left`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 3)))
        queueOperations.publishesRestoredQueue = true
        preferences.queueIds = "1,2,3"
        preferences.shuffleQueueIds = "1,2,3"
        preferences.queuePosition = 2

        initAndRestore()

        preferences.queueIds shouldBe "1,3"
        preferences.queuePosition shouldBe 1
    }

    @Test
    fun `a restore sets the saved shuffle mode with the queue, whether or not the mode was restored first`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(songs)
        preferences.shuffleMode = ShuffleMode.On
        preferences.queueIds = "1,2,3"
        preferences.shuffleQueueIds = "3,1,2"
        preferences.queuePosition = 0

        // The fake's shuffle mode stays off, as if restoring the mode on its own came after the queue.
        initAndRestore()

        queueOperations.shuffleModeFlow.value shouldBe ShuffleMode.Off
        queueOperations.lastSetQueueShuffleMode shouldBe ShuffleMode.On
        queueOperations.lastSetShuffleQueue?.map { song -> song.id } shouldBe listOf(3L, 1L, 2L)
        queueOperations.lastSetQueuePosition shouldBe 0
    }

    @Test
    fun `a restore whose saved song is gone starts the next one from the beginning`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 3)))
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.playbackPosition = 30_000

        initializer.init(application)

        awaitUntil { queueOperations.hasRestoredQueue }
        queueOperations.lastSetQueuePosition shouldBe 1
        playbackOperations.loadedPositions shouldBe listOf(0)
        preferences.playbackPosition shouldBe 0
    }

    @Test
    fun `a restore saved while an opened file played starts the saved song from the beginning`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(songs)
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.restoreQueuePositionFromStart = true
        preferences.playbackPosition = 30_000

        initializer.init(application)

        awaitUntil { queueOperations.hasRestoredQueue }
        queueOperations.lastSetQueuePosition shouldBe 1
        playbackOperations.loadedPositions shouldBe listOf(0)
    }

    @Test
    fun `a restore that throws still marks the queue restored, so requests waiting on it go ahead`() {
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val failingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error -> failures += error })
        val failingRepository = mockk<SongRepository> {
            every { getSongs(any()) } throws IllegalStateException("database unavailable")
        }
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 0

        try {
            createInitializer(failingRepository, failingScope).init(application)

            awaitUntil { queueOperations.hasRestoredQueue }
            awaitUntil { failures.isNotEmpty() }
            failures.single().message shouldBe "database unavailable"
        } finally {
            failingScope.cancel()
        }
    }

    @Test
    fun `a restore finishing after something else was played leaves that queue and its playback alone`() {
        val songsLoaded = CompletableDeferred<Unit>()
        val slowRepository = mockk<SongRepository> {
            every { getSongs(any()) } returns flow {
                songsLoaded.await()
                emit(songs)
            }
        }
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.playbackPosition = 30_000
        createInitializer(slowRepository, appCoroutineScope).init(application)

        // A request that gave up waiting for the restore sets its own queue.
        publishQueue(listOf(createSong(id = 9)), currentPosition = 0, contentVersion = 1)
        songsLoaded.complete(Unit)

        awaitUntil { queueOperations.hasRestoredQueue }
        queueOperations.lastSetQueue shouldBe null
        playbackOperations.loadedPositions shouldBe emptyList()
    }

    @Test
    fun `a restore reads and builds the queue without the main thread, then sets and loads it in one main thread step`() {
        val main = QueuedDispatcher()
        Dispatchers.setMain(main)
        val songsLoaded = CompletableDeferred<Unit>()
        val slowRepository = mockk<SongRepository> {
            every { getSongs(any()) } returns flow {
                songsLoaded.await()
                emit(songs)
            }
        }
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.playbackPosition = 30_000
        createInitializer(slowRepository, appCoroutineScope).init(application)
        // The collectors, and the shuffle and repeat modes.
        main.runPending()

        songsLoaded.complete(Unit)

        awaitUntil { main.pending == 1 }
        queueOperations.buildThreads.single() shouldNotBe Thread.currentThread()
        queueOperations.lastSetQueue shouldBe null
        playbackOperations.loadedPositions shouldBe emptyList()
        queueOperations.hasRestoredQueue shouldBe false

        main.runPending() shouldBe 1

        queueOperations.setQueueThreads shouldBe listOf(Thread.currentThread())
        queueOperations.lastSetQueue shouldBe songs
        queueOperations.lastSetQueuePosition shouldBe 1
        playbackOperations.loadedPositions shouldBe listOf(30_000)
        queueOperations.hasRestoredQueue shouldBe true
    }

    /**
     * Inits with the main thread on the test's, waits for the restore to finish, then runs what's left on the main
     * thread (the collectors handling the restored queue), so what the restore saves is settled.
     */
    private fun initAndRestore(): QueuedDispatcher {
        val main = QueuedDispatcher()
        Dispatchers.setMain(main)
        initializer.init(application)
        awaitUntil {
            main.runPending()
            queueOperations.hasRestoredQueue
        }
        main.runUntilIdle()
        return main
    }

    /** A main thread that runs what's dispatched to it only when the test says, on the test's thread. */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()

        val pending: Int get() = tasks.size

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable
        ) {
            tasks += block
        }

        /** Runs what's been dispatched so far, and returns how many there were. */
        fun runPending(): Int {
            var count = 0
            repeat(tasks.size) {
                tasks.poll()?.run()
                count++
            }
            return count
        }

        /** Runs what's dispatched, and what that dispatches, until there's nothing left. */
        fun runUntilIdle() {
            while (runPending() > 0) Unit
        }
    }

    /** Waits for a write the initializer hands off to [kotlinx.coroutines.Dispatchers.IO]. */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for the condition" }
            Thread.sleep(10)
        }
    }

    private fun publishQueue(
        currentPosition: Int,
        contentVersion: Long
    ) = publishQueue(songs, currentPosition, contentVersion)

    private fun publishQueue(
        songs: List<Song>,
        currentPosition: Int,
        contentVersion: Long
    ) {
        val items = songs.mapIndexed { index, song -> song.toQueueItem(isCurrent = index == currentPosition) }
        val previous = queueOperations.queueStateFlow.value
        queueOperations.queueStateFlow.value = QueueState(
            items = items,
            currentItem = items[currentPosition],
            currentPosition = currentPosition,
            version = previous.version + 1,
            contentVersion = contentVersion
        )
    }
}
