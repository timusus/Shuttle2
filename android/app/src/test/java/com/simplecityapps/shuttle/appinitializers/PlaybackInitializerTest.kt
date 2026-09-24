package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PositionAnchor
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.testing.MainDispatcherRule
import com.squareup.moshi.Moshi
import dagger.Lazy
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.Collections
import kotlinx.coroutines.CoroutineExceptionHandler
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
    private val playbackManager = FakePlaybackManager()
    private val songRepository = FakeSongRepository()
    private val queueManager = FakeQueueManager()
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
        playbackManager = playbackManager,
        queueManager = queueManager,
        playbackPreferenceManager = preferences,
        castSessionManager = Lazy {
            startedComponents += "cast"
            mockk(relaxed = true)
        },
        mediaSessionManager = Lazy {
            startedComponents += "media session"
            mockk(relaxed = true)
        },
        noiseManager = Lazy {
            startedComponents += "noise"
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
        preferences.shuffleMode = QueueManager.ShuffleMode.On
        preferences.repeatMode = QueueManager.RepeatMode.All
        playbackManager.playbackStateFlow.value = PlaybackState.Playing
        playbackManager.progressFlow.value = PlaybackProgress(position = 90_000, duration = 200_000)

        initializer.init(application)

        preferences.queueIds shouldBe "7,8"
        preferences.shuffleMode shouldBe QueueManager.ShuffleMode.On
        preferences.repeatMode shouldBe QueueManager.RepeatMode.All
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

        queueManager.shuffleModeFlow.value = QueueManager.ShuffleMode.On
        queueManager.repeatModeFlow.value = QueueManager.RepeatMode.One

        preferences.shuffleMode shouldBe QueueManager.ShuffleMode.On
        preferences.repeatMode shouldBe QueueManager.RepeatMode.One
    }

    @Test
    fun `playback starting starts the playback service`() {
        initializer.init(application)

        playbackManager.playbackStateFlow.value = PlaybackState.Playing

        shadowOf(application).nextStartedService?.component?.className shouldBe PlaybackService::class.java.name
    }

    @Test
    fun `progress is saved immediately, then throttled to once it has moved by more than a second`() {
        initializer.init(application)

        // The first observed progress is the initial anchor: no prior save to compare against, so it
        // writes straight away rather than waiting for a second tick to establish a baseline.
        playbackManager.progressFlow.value = PlaybackProgress(position = 5_000, duration = 200_000)
        preferences.playbackPosition shouldBe 5_000

        playbackManager.progressFlow.value = PlaybackProgress(position = 5_900, duration = 200_000)
        preferences.playbackPosition shouldBe 5_000

        playbackManager.progressFlow.value = PlaybackProgress(position = 6_500, duration = 200_000)
        preferences.playbackPosition shouldBe 6_500
    }

    @Test
    fun `sub-second progress ticks in either direction do not write to preferences`() {
        initializer.init(application)
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // Small forward and backward ticks stay under the throttle: no further writes.
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_400, duration = 200_000)
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_100, duration = 200_000)
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_700, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000
    }

    @Test
    fun `restarting a track saves the reset position, so a force-stop restores from 0`() {
        initializer.init(application)

        playbackManager.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // Restarting the track seeks back to 0 - a discontinuity, so it's saved immediately, rather
        // than being masked by the old (higher) high-water mark. A restore after a force-stop reads
        // this value straight back as the seek position.
        playbackManager.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 0, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 0
    }

    @Test
    fun `seeking backwards saves the earlier position immediately`() {
        initializer.init(application)

        playbackManager.progressFlow.value = PlaybackProgress(position = 90_000, duration = 200_000)
        preferences.playbackPosition shouldBe 90_000

        playbackManager.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 30_000, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 30_000
    }

    @Test
    fun `a track change saves the new track's position immediately, even under the throttle`() {
        initializer.init(application)

        playbackManager.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // The new track starts close to the old saved position - under the throttle, but a track
        // change is a discontinuity, so it's still saved immediately.
        playbackManager.positionAnchorFlow.value = PositionAnchor(PlaybackState.Playing, positionMs = 65_200, elapsedRealtimeMs = 1_000, speed = 1f)

        preferences.playbackPosition shouldBe 65_200
    }

    @Test
    fun `init starts the playback components that run for the life of the app`() {
        startedComponents shouldBe emptyList()

        initializer.init(application)

        startedComponents shouldBe listOf("cast", "media session", "noise", "bit-perfect")
    }

    @Test
    fun `a cleared saved position lets the next progress be saved straight away`() {
        initializer.init(application)
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // The playback manager clears it on a pause with no position.
        preferences.playbackPosition = null

        // Under a second from the cleared position: with no saved position there's nothing to throttle against.
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_500, duration = 200_000)
        preferences.playbackPosition shouldBe 65_500
    }

    @Test
    fun `progress is throttled against the saved position, even one the playback manager saved`() {
        initializer.init(application)
        playbackManager.progressFlow.value = PlaybackProgress(position = 195_000, duration = 200_000)
        preferences.playbackPosition shouldBe 195_000

        // A track end resets the saved position to 0 for the next track.
        preferences.playbackPosition = 0

        playbackManager.progressFlow.value = PlaybackProgress(position = 400, duration = 200_000)
        preferences.playbackPosition shouldBe 0

        playbackManager.progressFlow.value = PlaybackProgress(position = 1_200, duration = 200_000)
        preferences.playbackPosition shouldBe 1_200
    }

    @Test
    fun `a track end records the song as played through`() {
        initializer.init(application)
        val endedSong = createSong(id = 4, duration = 200_000)

        playbackManager.trackEndedFlow.tryEmit(endedSong)

        awaitUntil { songRepository.playCountIncrements.isNotEmpty() }
        songRepository.playbackPositions.toList() shouldBe listOf(4L to 200_000)
        songRepository.playCountIncrements.toList() shouldBe listOf(4L)
    }

    @Test
    fun `a pause records the song's position`() {
        initializer.init(application)
        val pausedSong = createSong(id = 5, duration = 200_000)

        playbackManager.pausePositionFlow.tryEmit(SongPosition(pausedSong, 42_000))

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
    fun `a restore drops songs no longer in the library and keeps the position on the saved song`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 3), createSong(id = 4)))
        preferences.queueIds = "1,2,3,1,4"
        preferences.queuePosition = 4
        preferences.playbackPosition = 30_000

        initializer.init(application)

        awaitUntil { queueManager.hasRestoredQueue }
        queueManager.lastSetQueue?.map { song -> song.id } shouldBe listOf(1L, 3L, 1L, 4L)
        queueManager.lastSetQueuePosition shouldBe 3
        playbackManager.loadedPositions shouldBe listOf(30_000)
    }

    @Test
    fun `a restore whose saved song is gone starts the next one from the beginning`() {
        songRepository.applyQueryPredicates = true
        songRepository.setSongs(listOf(createSong(id = 1), createSong(id = 3)))
        preferences.queueIds = "1,2,3"
        preferences.queuePosition = 1
        preferences.playbackPosition = 30_000

        initializer.init(application)

        awaitUntil { queueManager.hasRestoredQueue }
        queueManager.lastSetQueuePosition shouldBe 1
        playbackManager.loadedPositions shouldBe listOf(0)
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

        awaitUntil { queueManager.hasRestoredQueue }
        queueManager.lastSetQueuePosition shouldBe 1
        playbackManager.loadedPositions shouldBe listOf(0)
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

            awaitUntil { queueManager.hasRestoredQueue }
            awaitUntil { failures.isNotEmpty() }
            failures.single().message shouldBe "database unavailable"
        } finally {
            failingScope.cancel()
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
        val previous = queueManager.queueStateFlow.value
        queueManager.queueStateFlow.value = QueueState(
            items = items,
            currentItem = items[currentPosition],
            currentPosition = currentPosition,
            version = previous.version + 1,
            contentVersion = contentVersion
        )
    }
}
