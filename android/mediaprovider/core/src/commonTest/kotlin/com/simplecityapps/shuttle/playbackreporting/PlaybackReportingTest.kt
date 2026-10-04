package com.simplecityapps.shuttle.playbackreporting

import com.simplecityapps.mediaprovider.AggregatePlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackReporter
import com.simplecityapps.mediaprovider.PlaybackSession
import com.simplecityapps.mediaprovider.settings.LibrarySettings
import com.simplecityapps.playback.CastDevice
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.TrackEnd
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.InMemoryKeyValueStore
import com.simplecityapps.shuttle.settings.SettingsStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Covers [PlaybackReporting], the flow wiring each platform starts once (#96, #773). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackReportingTest {
    private val song = createSong(id = 1, duration = 200_000, mediaProvider = MediaProviderType.Jellyfin)
    private val playbackOperations = FakePlaybackOperations()
    private val queueOperations = FakeQueueOperations()
    private val reporter = FakeReporter()
    private val playbackReporter = AggregatePlaybackReporter(setOf(reporter))
    private val librarySettings = LibrarySettings(SettingsStore(InMemoryKeyValueStore()))

    // Set in setUp, once the main dispatcher below is in place: a scope built before that would capture
    // whatever Main was then, and the sender's work loop would never run on a target with a real one (iOS).
    private lateinit var appCoroutineScope: CoroutineScope

    private val playbackReporting by lazy {
        PlaybackReporting(
            playbackOperations = playbackOperations,
            queueOperations = queueOperations,
            playbackReporter = playbackReporter,
            sender = PlaybackReportSender(
                reporter = playbackReporter,
                pendingPlays = PendingPlays(InMemoryKeyValueStore()),
                findSongs = { emptyList() },
                isEnabled = { librarySettings.reportPlaybackToServer.value },
                now = { Instant.fromEpochMilliseconds(0) },
                scope = appCoroutineScope
            ),
            librarySettings = librarySettings,
            appCoroutineScope = appCoroutineScope
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        appCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    @AfterTest
    fun tearDown() {
        appCoroutineScope.cancel()
        Dispatchers.resetMain()
    }

    private fun playSongAt(positionMs: Int) {
        queueOperations.queueStateFlow.value = song.toQueueItem(isCurrent = true).let { item -> QueueState(items = listOf(item), currentItem = item, currentPosition = 0) }
        playbackOperations.progressFlow.value = PlaybackProgress(position = positionMs, duration = song.duration)
        playbackOperations.playbackStateFlow.value = PlaybackState.Playing
    }

    @Test
    fun `a song already playing when reporting attaches starts at its position`() {
        playSongAt(90_000)

        playbackReporting.start()

        reporter.calls shouldBe listOf("start 90000")
    }

    @Test
    fun `a second start changes nothing`() {
        playSongAt(90_000)

        playbackReporting.start()
        playbackReporting.start()

        reporter.calls shouldBe listOf("start 90000")
    }

    @Test
    fun `turning reporting on mid-song starts a play at the current position`() {
        librarySettings.reportPlaybackToServer.value = false
        playSongAt(30_000)
        playbackReporting.start()
        reporter.calls.shouldBeEmpty()

        librarySettings.reportPlaybackToServer.value = true

        reporter.calls shouldBe listOf("start 30000")
    }

    @Test
    fun `turning reporting off mid-song stops the play`() {
        playSongAt(30_000)
        playbackReporting.start()

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

/** Every member [PlaybackReporting] doesn't read. */
private fun unused(): Nothing = error("Not read by PlaybackReporting")

private class FakePlaybackOperations : PlaybackOperations {
    override val playbackStateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Paused)
    override val progressFlow = MutableStateFlow<PlaybackProgress?>(null)
    override val playbackSpeedFlow = MutableStateFlow(1f)
    override val castDeviceFlow = MutableStateFlow<CastDevice?>(null)
    override val trackEndedFlow = MutableSharedFlow<TrackEnd>(extraBufferCapacity = 64)
    override val pausePositionFlow = MutableSharedFlow<SongPosition>(extraBufferCapacity = 64)
    override val playbackFailureFlow = MutableSharedFlow<Song>(extraBufferCapacity = 64)

    override fun load(seekPosition: Int?, skipUnloadable: Boolean, playWhenReady: Boolean, completion: (Result<Boolean>) -> Unit) = unused()
    override fun play() = unused()
    override fun pause() = unused()
    override fun togglePlayback() = unused()
    override fun skipToNext(ignoreRepeat: Boolean, completion: ((Result<Any?>) -> Unit)?) = unused()
    override fun skipToPrev(force: Boolean, completion: ((Result<Any?>) -> Unit)?) = unused()
    override fun skipTo(position: Int) = unused()
    override suspend fun addToQueue(songs: List<Song>) = unused()
    override suspend fun playNext(songs: List<Song>) = unused()
    override suspend fun shuffle(songs: List<Song>, context: PlayContext, completion: (Result<Any?>) -> Unit) = unused()
    override fun seekTo(position: Int) = unused()
    override fun playbackState(): PlaybackState = unused()
    override fun getProgress(): Int? = unused()
    override fun getDuration(): Int? = unused()
    override fun getPlaybackSpeed(): Float = unused()
    override fun setPlaybackSpeed(multiplier: Float) = unused()
    override fun moveQueueItem(from: Int, to: Int) = unused()
    override fun removeQueueItem(queueItem: QueueItem) = unused()
    override fun clearQueue() = unused()
}

private class FakeQueueOperations : QueueOperations {
    override val queueStateFlow = MutableStateFlow(QueueState(items = emptyList(), currentItem = null, currentPosition = null))
    override val shuffleModeFlow = MutableStateFlow<ShuffleMode>(ShuffleMode.Off)
    override val repeatModeFlow = MutableStateFlow<RepeatMode>(RepeatMode.Off)
    override var playContext: PlayContext = PlayContext.None
    override var hasRestoredQueue: Boolean = false

    override suspend fun setQueue(songs: List<Song>, shuffleSongs: List<Song>?, position: Int, context: PlayContext): Boolean = unused()
    override fun getQueue(): List<QueueItem> = unused()
    override fun getQueue(shuffleMode: ShuffleMode): List<QueueItem> = unused()
    override fun getCurrentItem(): QueueItem? = unused()
    override fun getCurrentPosition(): Int? = unused()
    override fun getSize(): Int = unused()
    override fun setCurrentItem(currentItem: QueueItem) = unused()
    override fun getNext(ignoreRepeat: Boolean): QueueItem? = unused()
    override fun getPrevious(): QueueItem? = unused()
    override fun skipToNext(ignoreRepeat: Boolean): Boolean = unused()
    override fun skipToPrevious() = unused()
    override fun skipTo(position: Int) = unused()
    override suspend fun addToQueue(songs: List<Song>): Boolean = unused()
    override suspend fun addToNext(songs: List<Song>): Boolean = unused()
    override fun updateSongs(songs: List<Song>) = unused()
    override fun move(from: Int, to: Int) = unused()
    override fun remove(items: List<QueueItem>) = unused()
    override fun remove(song: Song) = unused()
    override fun clear() = unused()
    override fun getShuffleMode(): ShuffleMode = unused()
    override suspend fun setShuffleMode(shuffleMode: ShuffleMode, reshuffle: Boolean) = unused()
    override suspend fun toggleShuffleMode() = unused()
    override fun getRepeatMode(): RepeatMode = unused()
    override fun setRepeatMode(repeatMode: RepeatMode) = unused()
    override fun toggleRepeatMode() = unused()
}
