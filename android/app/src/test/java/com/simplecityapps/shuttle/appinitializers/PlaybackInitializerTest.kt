package com.simplecityapps.shuttle.appinitializers

import android.app.Application
import android.content.Context
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackManager
import com.simplecityapps.fakes.FakeQueueManager
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackService
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PlaybackWatcher
import com.simplecityapps.playback.PositionAnchor
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.testing.MainDispatcherRule
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import io.mockk.mockk
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
    private val queueManager = FakeQueueManager()
    private val playbackWatcher = PlaybackWatcher()
    private val preferences = PlaybackPreferenceManager(
        application.getSharedPreferences("playback_initializer_test", Context.MODE_PRIVATE),
        Moshi.Builder().build()
    )
    private val appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val initializer = PlaybackInitializer(
        context = application,
        songRepository = FakeSongRepository(),
        playbackManager = playbackManager,
        playbackWatcher = playbackWatcher,
        queueManager = queueManager,
        playbackPreferenceManager = preferences,
        castSessionManager = mockk(relaxed = true),
        mediaSessionManager = mockk(relaxed = true),
        noiseManager = mockk(relaxed = true),
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
    fun `pausing with no position clears the saved one, so the next progress is saved straight away`() {
        initializer.init(application)
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_000, duration = 200_000)
        preferences.playbackPosition shouldBe 65_000

        // The fake playback manager reports no position.
        playbackWatcher.onPlaybackStateChanged(PlaybackState.Paused)
        preferences.playbackPosition shouldBe null

        // Under a second from the cleared position: with no saved position there's nothing to throttle against.
        playbackManager.progressFlow.value = PlaybackProgress(position = 65_500, duration = 200_000)
        preferences.playbackPosition shouldBe 65_500
    }

    @Test
    fun `onTrackEnded saves 0 only when the queue has a next item to move to`() {
        initializer.init(application)
        playbackManager.progressFlow.value = PlaybackProgress(position = 195_000, duration = 200_000)
        preferences.playbackPosition shouldBe 195_000

        val endedSong = songs[0]
        queueManager.nextItem = null
        playbackWatcher.onTrackEnded(endedSong)

        // No next item to move to - the position of the song that just finished is left alone.
        preferences.playbackPosition shouldBe 195_000

        queueManager.nextItem = songs[1].toQueueItem(isCurrent = false)
        playbackWatcher.onTrackEnded(endedSong)

        preferences.playbackPosition shouldBe 0
    }

    private fun publishQueue(
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
