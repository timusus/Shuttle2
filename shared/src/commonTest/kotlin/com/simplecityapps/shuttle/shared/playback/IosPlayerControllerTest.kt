package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.entitlement.Entitlement
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.entitlement.ServerAccess
import com.simplecityapps.shuttle.entitlement.ServerAccessGate
import com.simplecityapps.shuttle.model.PlayContext
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.shared.entitlement.GatedServerStreams
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The controller over a fake engine: Android's playback semantics (`PlaybackFacade`, `QueueFacade`, `ItemLoader`), and
 * that the engine is always handed the queue's current item and the one after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerTest {
    private val engine = FakeIosAudioPlayer()

    /** Songs the resolver can't find a stream for. */
    private val unresolvable = mutableSetOf<Long>()

    private val a = song(1)
    private val b = song(2)
    private val c = song(3)
    private val d = song(4)
    private val e = song(5)

    /** The ids of the songs resolved, in order. */
    private val resolved = mutableListOf<Long>()

    /** The play each resolution was for, by song id, in order. */
    private val plays = mutableListOf<Pair<Long, String>>()

    /** The plays the controller ended, in order. */
    private val endedPlays = mutableListOf<String>()

    /** Songs on a server, whose streams open at a position (`StartTimeTicks`). */
    private val server = mutableSetOf<Long>()

    /** Whether a [server] song may stream, given whether the user asked to play it; the real gate in the gate tests. */
    private var serverAccess: suspend (Song, Boolean) -> ServerAccess = { _, _ -> ServerAccess.Allowed }

    private fun url(song: Song) = "song:${song.id}"

    /** While set, every stream resolution waits for it: a slow server answer. */
    private var resolveGate: CompletableDeferred<Unit>? = null

    /**
     * Runs [block] over a controller on an unconfined test dispatcher; or, if not [unconfined], on a queued one, which
     * doesn't hold back the coroutines resumed while the controller works on main, as main doesn't.
     */
    private fun test(
        unconfined: Boolean = true,
        block: suspend TestScope.(IosPlayerController) -> Unit
    ): TestResult = runTest {
        val dispatcher = if (unconfined) UnconfinedTestDispatcher(testScheduler) else StandardTestDispatcher(testScheduler)
        val controller = IosPlayerController(
            player = engine,
            resolver = object : IosStreamResolver {
                override suspend fun resolve(
                    song: Song,
                    startPositionMs: Long,
                    playRequested: Boolean,
                    playId: String
                ): IosStream {
                    resolved += song.id
                    plays += song.id to playId
                    resolveGate?.await()
                    if (song.id in server) {
                        when (serverAccess(song, playRequested)) {
                            ServerAccess.Allowed -> Unit
                            ServerAccess.Refused -> throw ServerStreamNotAllowedException(song, undecided = false)
                            ServerAccess.Undecided -> throw ServerStreamNotAllowedException(song, undecided = true)
                        }
                    }
                    return when {
                        song.id in unresolvable -> error("No stream for ${song.name}")
                        song.id in server && startPositionMs > 0 -> IosStream("${url(song)}?from=$startPositionMs", opensAtPosition = true)
                        else -> IosStream(url(song), opensAtPosition = song.id in server)
                    }
                }

                override suspend fun endPlay(
                    song: Song,
                    playId: String
                ) {
                    endedPlays += playId
                }
            },
            scope = CoroutineScope(dispatcher),
            random = Random(1)
        )
        block(controller)
    }

    private fun <T> TestScope.collect(flow: Flow<T>): List<T> {
        val values = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(values) }
        return values
    }

    /** Sets [songs] as the queue at [position], loads it and, if [play], plays it. */
    private suspend fun IosPlayerController.start(
        songs: List<Song>,
        position: Int = 0,
        play: Boolean = true
    ) {
        queueOperations.setQueue(songs, null, position)
        load { }
        engine.settle()
        if (play) {
            play()
            engine.settle()
        }
        engine.clearCalls()
    }

    private val IosPlayerController.currentSong
        get() = queueOperations.getCurrentItem()?.song

    // Playing through the queue

    @Test
    fun `loading the queue loads its first song paused - with the second handed over as next`() = test { controller ->
        var result: Result<Boolean>? = null
        controller.queueOperations.setQueue(listOf(a, b, c), null, 0)

        controller.load { result = it }
        controller.playbackState() shouldBe PlaybackState.Loading
        engine.settle()

        result shouldBe Result.success(true)
        engine.calls shouldBe listOf("load song:1@0", "next song:2")
        controller.playbackState() shouldBe PlaybackState.Paused

        controller.play()
        engine.settle()
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a load replaced before its item is ready is dropped - and the new one completes`() = test { controller ->
        val replaced = mutableListOf<Result<Boolean>>()
        val loaded = mutableListOf<Result<Boolean>>()
        controller.queueOperations.setQueue(listOf(a, b), null, 0)

        controller.load { replaced += it }
        controller.load { loaded += it }
        replaced.single().exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        engine.settle()

        replaced.size shouldBe 1
        loaded shouldBe listOf(Result.success(true))
        controller.playbackState() shouldBe PlaybackState.Paused

        // A skip replaces a load in flight the same way.
        controller.load { replaced += it }
        controller.skipToNext { loaded += Result.success(it.isSuccess) }
        replaced.last().exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        engine.settle()
        loaded.last() shouldBe Result.success(true)
    }

    @Test
    fun `playing out a song moves on to the next gaplessly - and reports it ended`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b, c))

        engine.finishTrack()

        controller.currentSong shouldBe b
        ended.map { it.song } shouldBe listOf(a)
        engine.calls shouldBe listOf("next song:3")
        controller.progressFlow.value shouldBe PlaybackProgress(0, b.duration)
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `skipping to the next and previous songs plays them from the start`() = test { controller ->
        controller.start(listOf(a, b, c))
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }
        engine.settle()

        controller.currentSong shouldBe b
        result?.isSuccess shouldBe true
        engine.calls.first() shouldBe "load song:2@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing

        controller.skipToPrev()
        engine.settle()
        controller.currentSong shouldBe a
    }

    @Test
    fun `skipping onto the next hands the engine its stream back as current - without resolving it again`() = test { controller ->
        controller.start(listOf(a, b, c))
        val next = checkNotNull(engine.next)
        resolved.clear()

        controller.skipToNext()
        engine.settle()

        controller.currentSong shouldBe b
        engine.calls shouldBe listOf("load song:2@0 playing", "next song:3")
        // The engine keeps what it pre-opened for the same stream, under the new hand-over's id.
        engine.current?.url shouldBe next.url
        engine.current?.id shouldNotBe next.id
        resolved shouldBe listOf(c.id)
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `skipping past the next resolves the song skipped to`() = test { controller ->
        controller.start(listOf(a, b, c, d))
        resolved.clear()

        controller.skipTo(2)
        engine.settle()

        controller.currentSong shouldBe c
        resolved shouldBe listOf(c.id, d.id)
    }

    @Test
    fun `skipping back past the first moments of a song restarts it`() = test { controller ->
        controller.start(listOf(a, b, c), position = 1)
        engine.tick(5_000)

        controller.skipToPrev()

        controller.currentSong shouldBe b
        engine.calls shouldBe listOf("seek 0")

        controller.skipToPrev(force = true)
        controller.currentSong shouldBe a
    }

    @Test
    fun `skipping next at the end of the queue fails with repeat off`() = test { controller ->
        controller.start(listOf(a, b), position = 1)
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }

        result?.isFailure shouldBe true
        controller.currentSong shouldBe b
        engine.calls shouldBe emptyList()
    }

    // Repeat

    @Test
    fun `at the end of the queue with repeat off - playback pauses - and playing again restarts the last song`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b), position = 1)
        engine.next shouldBe null

        engine.finishTrack()

        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Paused
        ended.map { it.song } shouldBe listOf(b)

        controller.play()
        engine.settle()
        engine.calls.first() shouldBe "load song:2@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `with repeat all - the first song is handed over after the last`() = test { controller ->
        controller.start(listOf(a, b), position = 1)

        controller.queueOperations.setRepeatMode(RepeatMode.All)
        engine.next?.url shouldBe url(a)

        engine.finishTrack()
        controller.currentSong shouldBe a
        engine.next?.url shouldBe url(b)
    }

    @Test
    fun `with repeat one - the same song is handed over again under a new id`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b))
        val uid = controller.queueOperations.getCurrentItem()?.uid

        controller.queueOperations.setRepeatMode(RepeatMode.One)
        val repeat = engine.next
        repeat?.url shouldBe url(a)
        repeat?.id shouldNotBe engine.current?.id

        engine.finishTrack()
        controller.queueOperations.getCurrentItem()?.uid shouldBe uid
        ended.map { it.song } shouldBe listOf(a)
        engine.next?.url shouldBe url(a)
        engine.next?.id shouldNotBe repeat?.id

        controller.queueOperations.setRepeatMode(RepeatMode.Off)
        engine.next?.url shouldBe url(b)
    }

    // Shuffle

    @Test
    fun `shuffle on and off keeps the current song playing and hands over the next in the new order`() = test { controller ->
        controller.start(listOf(a, b, c, d, e), position = 2)

        controller.queueOperations.toggleShuffleMode()

        controller.queueOperations.getShuffleMode() shouldBe ShuffleMode.On
        controller.currentSong shouldBe c
        controller.queueOperations.getCurrentPosition() shouldBe 0
        engine.next?.url shouldBe url(controller.queueOperations.getQueue()[1].song)
        engine.calls.none { it.startsWith("load") } shouldBe true

        controller.queueOperations.toggleShuffleMode()

        controller.currentSong shouldBe c
        controller.queueOperations.getCurrentPosition() shouldBe 2
        engine.next?.url shouldBe url(d)
        engine.calls.none { it.startsWith("load") } shouldBe true
    }

    @Test
    fun `shuffling songs turns shuffle on and loads the first shuffled song without playing it`() = test { controller ->
        var result: Result<Any?>? = null

        controller.shuffle(listOf(a, b, c, d)) { result = it }
        engine.settle()

        result?.isSuccess shouldBe true
        controller.queueOperations.getShuffleMode() shouldBe ShuffleMode.On
        controller.queueOperations.getCurrentPosition() shouldBe 0
        engine.current?.url shouldBe url(controller.queueOperations.getQueue()[0].song)
        engine.next?.url shouldBe url(controller.queueOperations.getQueue()[1].song)
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a shuffle's context is set before its first song becomes current - as a listen reads it then`() = test(unconfined = false) { controller ->
        controller.queueOperations.setQueue(listOf(a, b), context = PlayContext.Playlist(1))
        val contexts = mutableListOf<PlayContext>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.queueOperations.queueStateFlow.distinctUntilChangedBy { it.currentItem?.uid }.collect {
                contexts += controller.queueOperations.playContext
            }
        }

        controller.shuffle(listOf(c, d, e), PlayContext.Genre("Jazz")) { }

        contexts shouldBe listOf(PlayContext.Playlist(1), PlayContext.Genre("Jazz"))
    }

    // Queue edits

    @Test
    fun `moving the next song away hands over the new next`() = test { controller ->
        controller.start(listOf(a, b, c, d))

        controller.moveQueueItem(1, 3)

        controller.queueOperations.getQueue().map { it.song } shouldBe listOf(a, c, d, b)
        engine.next?.url shouldBe url(c)
        engine.calls.none { it.startsWith("load") } shouldBe true
    }

    @Test
    fun `moving the current song keeps playing it`() = test { controller ->
        controller.start(listOf(a, b, c, d))

        controller.moveQueueItem(0, 2)

        controller.currentSong shouldBe a
        controller.queueOperations.getCurrentPosition() shouldBe 2
        engine.next?.url shouldBe url(d)
        engine.calls.none { it.startsWith("load") } shouldBe true
    }

    @Test
    fun `removing the next song hands over the one after`() = test { controller ->
        controller.start(listOf(a, b, c))

        controller.removeQueueItem(controller.queueOperations.getQueue()[1])

        engine.next?.url shouldBe url(c)
        controller.currentSong shouldBe a
    }

    @Test
    fun `removing the current song plays on with the next`() = test { controller ->
        controller.start(listOf(a, b, c))

        controller.removeQueueItem(controller.queueOperations.getQueue()[0])
        engine.settle()

        controller.currentSong shouldBe b
        engine.calls.first { it.startsWith("load") } shouldBe "load song:2@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `removing the only song empties the queue and stops`() = test { controller ->
        controller.start(listOf(a))

        controller.removeQueueItem(controller.queueOperations.getQueue()[0])
        engine.settle()

        controller.queueOperations.getSize() shouldBe 0
        engine.calls shouldContain "stop"
        controller.playbackState() shouldBe PlaybackState.Paused
        controller.getProgress() shouldBe null
    }

    @Test
    fun `removing the current song with all after it ends playback - paused at the first song`() = test { controller ->
        controller.start(listOf(a, b, c), position = 1)
        val (_, itemB, itemC) = controller.queueOperations.getQueue()

        controller.queueOperations.remove(listOf(itemB, itemC))
        engine.settle()

        controller.currentSong shouldBe a
        engine.calls.first { it.startsWith("load") } shouldBe "load song:1@0"
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `songs played next are handed over as next`() = test { controller ->
        controller.start(listOf(a, b))

        controller.playNext(listOf(e))

        engine.next?.url shouldBe url(e)
        controller.queueOperations.getQueue().map { it.song } shouldBe listOf(a, e, b)
    }

    @Test
    fun `songs added to an empty queue play`() = test { controller ->
        controller.addToQueue(listOf(a, b))
        engine.settle()

        engine.calls.take(2) shouldBe listOf("load song:1@0 playing", "next song:2")
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `clearing the queue while playing keeps the current song`() = test { controller ->
        controller.start(listOf(a, b, c))

        controller.clearQueue()

        controller.queueOperations.getQueue().map { it.song } shouldBe listOf(a)
        engine.next shouldBe null
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `clearing the queue while paused empties it and stops`() = test { controller ->
        controller.start(listOf(a, b, c), play = false)

        controller.clearQueue()

        controller.queueOperations.getSize() shouldBe 0
        engine.calls shouldContain "stop"
    }

    @Test
    fun `new song data for the next song is handed over again`() = test { controller ->
        controller.start(listOf(a, b))

        controller.queueOperations.updateSongs(listOf(b.copy(name = "Renamed")))

        engine.calls shouldBe listOf("next null", "next song:2")
    }

    // Songs that fail to load

    @Test
    fun `a song that fails to load is skipped for the next - and reported`() = test { controller ->
        val failures = collect(controller.playbackFailureFlow)
        engine.failing += url(b)
        controller.start(listOf(a, b, c))
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }
        engine.settle()

        controller.currentSong shouldBe c
        failures shouldBe listOf(b)
        result shouldBe Result.success(false)
        engine.calls shouldContain "load song:3@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a song whose stream can't be resolved is skipped without a failure`() = test { controller ->
        val failures = collect(controller.playbackFailureFlow)
        unresolvable += b.id
        controller.start(listOf(a, b, c))

        controller.skipToNext()
        engine.settle()

        controller.currentSong shouldBe c
        failures shouldBe emptyList()
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    // The Shuttle Music Pro gate

    @Test
    fun `a server queue restored at launch doesn't open the paywall - playing it does`() = test { controller ->
        val gate = ServerAccessGate(MutableStateFlow(Entitlement.Free(trialUsed = true)), startTrial = null)
        val streams = GatedServerStreams(gate)
        serverAccess = streams::access
        server += listOf(a.id, b.id)
        val paywalls = collect(gate.paywallRequests)
        val skipped = collect(streams.gatedSongs)
        controller.queueOperations.setQueue(listOf(a, b), null, 0)

        controller.load(skipUnloadable = false) { }
        engine.settle()

        paywalls shouldBe emptyList()
        skipped shouldBe emptyList()
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused

        controller.play()
        engine.settle()

        paywalls.first() shouldBe PaywallSource.ServerPlayback
        skipped.first() shouldBe a
    }

    @Test
    fun `a next song refused before StoreKit answers isn't kept as failed - it loads when playback reaches it`() = test { controller ->
        server += b.id
        serverAccess = { _, _ -> ServerAccess.Undecided }
        controller.start(listOf(a, b, c))
        serverAccess = { _, _ -> ServerAccess.Allowed }

        engine.finishTrack()

        controller.currentSong shouldBe b
        engine.calls shouldContain "load song:2@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a song refused before StoreKit answers stays current - and plays when asked again`() = test { controller ->
        server += a.id
        serverAccess = { _, _ -> ServerAccess.Undecided }
        controller.queueOperations.setQueue(listOf(a, b), null, 0)

        controller.play()
        engine.settle()

        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused

        serverAccess = { _, _ -> ServerAccess.Allowed }
        engine.clearCalls()
        controller.play()
        engine.settle()

        engine.calls shouldContain "load song:1@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a next song that fails is skipped when playback reaches it`() = test { controller ->
        val failures = collect(controller.playbackFailureFlow)
        val ended = collect(controller.trackEndedFlow)
        engine.failing += url(b)
        controller.start(listOf(a, b, c))

        engine.finishTrack()

        controller.currentSong shouldBe c
        failures shouldBe listOf(b)
        ended.map { it.song } shouldBe listOf(a)
        engine.calls shouldContain "load song:3@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a load gives up when nothing after the failed song loads - and doesn't wrap`() = test { controller ->
        engine.failing += listOf(url(b), url(c))
        controller.start(listOf(a, b, c))
        controller.queueOperations.setRepeatMode(RepeatMode.All)
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }
        engine.settle()

        result?.isFailure shouldBe true
        controller.currentSong shouldBe c
        engine.calls shouldNotContain "load song:1@0 playing"
        engine.calls.last() shouldBe "stop"
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a restored song that fails stays current - paused - and loads again when played`() = test { controller ->
        engine.failing += url(a)
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        var result: Result<Boolean>? = null

        controller.load(skipUnloadable = false) { result = it }
        engine.settle()

        result?.isFailure shouldBe true
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused

        engine.failing.clear()
        engine.clearCalls()
        controller.play()
        engine.settle()
        engine.calls.first() shouldBe "load song:1@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    // State, progress and position

    @Test
    fun `toggling playback pauses and plays`() = test { controller ->
        controller.start(listOf(a, b))

        controller.togglePlayback()
        engine.settle()
        controller.playbackState() shouldBe PlaybackState.Paused

        controller.togglePlayback()
        engine.settle()
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `progress follows the engine - with the song's duration until the engine knows it`() = test { controller ->
        controller.start(listOf(a, b))

        engine.tick(1_234)
        controller.progressFlow.value shouldBe PlaybackProgress(1_234, a.duration)

        engine.duration = 199_000
        engine.tick(2_000)
        controller.progressFlow.value shouldBe PlaybackProgress(2_000, 199_000)
        controller.getDuration() shouldBe 199_000
    }

    @Test
    fun `pausing reports the song and where it paused`() = test { controller ->
        val pauses = collect(controller.pausePositionFlow)
        controller.start(listOf(a, b))
        engine.tick(3_000)

        controller.pause()
        engine.settle()

        pauses.last() shouldBe SongPosition(a, 3_000)
    }

    @Test
    fun `a podcast resumes a little before where it was left`() = test { controller ->
        val podcast = song(9, path = "/podcasts/9.mp3", playbackPosition = 60_000)
        controller.queueOperations.setQueue(listOf(podcast), null, 0)

        controller.load { }

        engine.calls.first() shouldBe "load song:9@55000"
    }

    @Test
    fun `playing a song within its last moments restarts it`() = test { controller ->
        controller.start(listOf(a, b), play = false)
        controller.seekTo(a.duration - 100)
        engine.clearCalls()

        controller.play()

        engine.calls shouldBe listOf("seek 0", "play")
    }

    // Seeking a stream the engine can't seek (a progressive transcode)

    @Test
    fun `seeking a transcode re-opens its stream at the position - and positions count from there`() = test { controller ->
        server += a.id
        engine.unseekable += url(a)
        controller.start(listOf(a, b))

        controller.seekTo(30_000)
        engine.settle()

        engine.calls shouldBe listOf("seek 30000", "load song:1?from=30000@0 playing next song:2")
        controller.progressFlow.value?.position shouldBe 30_000
        controller.playbackState() shouldBe PlaybackState.Playing

        engine.tick(5_000)
        controller.progressFlow.value?.position shouldBe 35_000
        controller.getProgress() shouldBe 35_000
        engine.duration = 20_000
        controller.getDuration() shouldBe 50_000
    }

    @Test
    fun `a re-opened transcode seeks by re-opening again - back or forward`() = test { controller ->
        server += a.id
        engine.unseekable += url(a)
        controller.start(listOf(a, b))
        controller.seekTo(30_000)
        engine.settle()
        engine.clearCalls()

        controller.seekTo(10_000)
        engine.settle()
        controller.seekTo(0)
        engine.settle()

        engine.calls shouldBe listOf("load song:1?from=10000@0 playing next song:2", "load song:1@0 playing next song:2")
        controller.progressFlow.value?.position shouldBe 0
        controller.currentSong shouldBe a
    }

    @Test
    fun `re-opening a transcode hands the engine back the next it already has`() = test { controller ->
        server += a.id
        engine.unseekable += url(a)
        controller.start(listOf(a, b))
        val next = engine.next

        controller.seekTo(30_000)
        engine.settle()

        // The same track, id and all, so the engine keeps the stream it opened for it.
        engine.next shouldBe next
        engine.finishTrack()
        controller.currentSong shouldBe b
    }

    // Plays: the session a server's transcode runs under (#722)

    @Test
    fun `re-opening a transcode for a seek carries on its play`() = test { controller ->
        server += a.id
        engine.unseekable += url(a)
        controller.start(listOf(a, b))

        controller.seekTo(30_000)
        engine.settle()
        controller.seekTo(10_000)
        engine.settle()

        val playsOfA = plays.filter { it.first == a.id }.map { it.second }
        playsOfA.size shouldBe 3
        playsOfA.toSet().size shouldBe 1
        endedPlays shouldBe emptyList()
    }

    @Test
    fun `under repeat one the next play of the same song is a play of its own`() = test { controller ->
        controller.start(listOf(a, b))

        controller.queueOperations.setRepeatMode(RepeatMode.One)

        val playsOfA = plays.filter { it.first == a.id }.map { it.second }
        playsOfA.size shouldBe 2
        playsOfA.toSet().size shouldBe 2
        endedPlays shouldBe listOf(plays.single { it.first == b.id }.second)
    }

    @Test
    fun `skipping on ends the play it leaves and carries on the next one`() = test { controller ->
        controller.start(listOf(a, b, c))
        val playOfA = plays.single { it.first == a.id }.second

        controller.skipToNext()
        engine.settle()

        endedPlays shouldBe listOf(playOfA)
        plays.count { it.first == b.id } shouldBe 1
        controller.currentSong shouldBe b
    }

    @Test
    fun `playing a track out ends its play`() = test { controller ->
        controller.start(listOf(a, b))
        val playOfA = plays.single { it.first == a.id }.second

        engine.finishTrack()

        endedPlays shouldBe listOf(playOfA)
    }

    @Test
    fun `emptying the queue ends the current and next plays`() = test { controller ->
        controller.start(listOf(a, b))

        controller.queueOperations.clear()

        endedPlays.toSet() shouldBe plays.map { it.second }.toSet()
    }

    @Test
    fun `a direct-play server stream seeks in the engine`() = test { controller ->
        server += a.id
        controller.start(listOf(a, b))

        controller.seekTo(30_000)
        engine.settle()

        engine.calls shouldBe listOf("seek 30000")
        controller.getProgress() shouldBe 30_000
    }

    @Test
    fun `loading a transcode at a position opens it there`() = test { controller ->
        val podcast = song(9, path = "jellyfin://podcasts/9", playbackPosition = 60_000)
        server += podcast.id
        engine.unseekable += url(podcast)
        controller.queueOperations.setQueue(listOf(podcast), null, 0)
        var result: Result<Boolean>? = null

        controller.load { result = it }
        engine.settle()

        engine.calls shouldBe listOf("load song:9@55000", "load song:9?from=55000@0")
        result shouldBe Result.success(true)
        controller.getProgress() shouldBe 55_000
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a stream that can't be sought or re-opened plays on where it was`() = test { controller ->
        engine.unseekable += url(a)
        controller.start(listOf(a, b))
        engine.tick(4_000)

        controller.seekTo(30_000)
        engine.settle()

        engine.calls shouldBe listOf("seek 30000")
        controller.progressFlow.value?.position shouldBe 4_000
    }

    @Test
    fun `a report about a track the engine has since replaced is ignored`() = test { controller ->
        controller.start(listOf(a, b, c))
        controller.skipToNext()
        engine.settle()

        // A late report for the first song's track, from before the skip.
        engine.load(IosAudioTrack("stale", url(a), emptyMap(), 0f, -1), null, 0, playWhenReady = false)
        engine.settle()

        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    // Play intent, kept apart from the engine's state

    @Test
    fun `play intent follows play pause and toggle - and a load does not assume it`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.playWhenReadyFlow.value shouldBe false

        controller.load { }
        controller.playWhenReadyFlow.value shouldBe false
        engine.settle()

        controller.play()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing

        controller.pause()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe false

        controller.togglePlayback()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe true

        controller.togglePlayback()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `play pause and toggle while a song is still loading update intent without leaving loading`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a), null, 0)

        controller.load { }
        controller.playbackState() shouldBe PlaybackState.Loading
        controller.playWhenReadyFlow.value shouldBe false

        controller.play()
        controller.pause()
        controller.togglePlayback()

        // The engine hasn't reported ready, so the state is still loading; the intent moved with each call.
        controller.playbackState() shouldBe PlaybackState.Loading
        controller.playWhenReadyFlow.value shouldBe true
        engine.calls shouldContain "play"
        engine.calls shouldContain "pause"
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a play asked before the engine settles survives its queued paused report - and a stale track does not`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { }
        controller.play()
        controller.pause()
        controller.play()
        engine.emitState(IosAudioPlayerState.Paused, "stale")

        engine.settle()

        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
        controller.currentSong shouldBe a
    }

    @Test
    fun `a load that plays hands the engine its track to play and completes once it's playing`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        engine.clearCalls()
        var result: Result<Boolean>? = null

        controller.load(seekPosition = 5_000, playWhenReady = true) { result = it }

        controller.playWhenReadyFlow.value shouldBe true
        result shouldBe null
        engine.settle()

        // One load that plays, so the engine readies its output while the track opens (#687); no separate play.
        engine.calls.first { it.startsWith("load") } shouldBe "load ${url(a)}@5000 playing"
        engine.calls.none { it == "play" } shouldBe true
        result?.isSuccess shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a paused load's completion that plays keeps that newer intent`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a), null, 0)

        controller.load { result ->
            result.onSuccess { controller.play() }
        }
        engine.settle()

        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `an engine that refuses a playing load clears intent when it becomes ready and does not skip`() = test { controller ->
        engine.acceptsPlay = false
        controller.queueOperations.setQueue(listOf(a, b, c), null, 0)
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Loading
        result shouldBe null

        engine.settle()

        result?.isSuccess shouldBe true
        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
        engine.calls.none { it.startsWith("load song:3") } shouldBe true
    }

    @Test
    fun `a refusal's load completion that plays keeps the new intent`() = test { controller ->
        engine.acceptsPlay = false
        controller.queueOperations.setQueue(listOf(a, b), null, 0)

        controller.skipToNext {
            engine.acceptsPlay = true
            controller.play()
        }
        engine.settle()

        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a refused play of a ready song clears intent - a queue change stays paused - and an explicit next plays`() = test { controller ->
        controller.start(listOf(a, b, c), play = false)
        engine.acceptsPlay = false
        engine.clearCalls()

        controller.play()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe false
        controller.currentSong shouldBe a

        controller.queueOperations.setCurrentItem(controller.queueOperations.getQueue()[1])
        engine.settle()
        engine.calls.first { it.startsWith("load") } shouldBe "load song:2@0"
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused

        engine.acceptsPlay = true
        engine.clearCalls()
        controller.skipToNext()
        engine.settle()
        engine.calls.first() shouldBe "load song:3@0 playing"
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `an engine that is already paused and can't start reports it again and the queue stays put`() = test { controller ->
        controller.start(listOf(a, b), play = false)
        engine.acceptsPlay = false
        engine.clearCalls()

        controller.play()
        controller.playWhenReadyFlow.value shouldBe true
        engine.settle()

        controller.playWhenReadyFlow.value shouldBe false
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused
        engine.calls shouldBe listOf("play")
    }

    @Test
    fun `a play while playing is not refused and leaves no play pending`() = test { controller ->
        controller.start(listOf(a, b))
        engine.acceptsPlay = false

        controller.play()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing

        // A later tick is not taken for that play's answer.
        engine.tick(1_000)
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a play within the round trip of a pause keeps its intent through the pause's report`() = test { controller ->
        controller.start(listOf(a, b))

        // Both reach the engine before its paused report for the pause comes back (#708).
        controller.pause()
        controller.play()
        controller.playWhenReadyFlow.value shouldBe true
        engine.settle()

        engine.state shouldBe IosAudioPlayerState.Playing
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing

        // The engine's later paused report is not that pause's: it's answered.
        controller.pause()
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a play pause and play while a playing report is in flight keep the last intent`() = test { controller ->
        controller.start(listOf(a, b), play = false)

        controller.play()
        controller.pause()
        controller.play()
        engine.settle()

        engine.state shouldBe IosAudioPlayerState.Playing
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a play reaching an engine already playing a load is answered - and shows playing`() = test { controller ->
        controller.start(listOf(a, b), play = false)

        // The skip's load plays; a media-key play follows before its playing report comes back. The engine is already
        // playing, so the play changes nothing there, and the load's reports are superseded by it.
        controller.skipToNext()
        controller.play()
        engine.calls shouldContain "play"
        engine.settle()

        engine.state shouldBe IosAudioPlayerState.Playing
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing

        // And the engine is known to play: the next toggle pauses it.
        controller.togglePlayback()
        engine.settle()
        engine.state shouldBe IosAudioPlayerState.Paused
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `an engine that pauses itself clears intent - and the next toggle plays`() = test { controller ->
        controller.start(listOf(a, b))

        // A route change stopped the engine and it wouldn't start again (#716).
        engine.pauseItself()

        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
        engine.clearCalls()

        controller.togglePlayback()
        engine.settle()
        engine.calls shouldBe listOf("play")
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    // Buffering

    @Test
    fun `a playing track whose stream runs dry is loading - with the intent kept - until it plays again`() = test { controller ->
        controller.start(listOf(a, b))
        val id = checkNotNull(engine.current).id

        engine.emitState(IosAudioPlayerState.Loading, id)
        engine.settle()

        controller.playbackState() shouldBe PlaybackState.Loading
        controller.bufferingFlow.value shouldBe true
        controller.playWhenReadyFlow.value shouldBe true

        engine.emitState(IosAudioPlayerState.Playing, id)
        engine.settle()

        controller.playbackState() shouldBe PlaybackState.Playing
        controller.bufferingFlow.value shouldBe false
    }

    @Test
    fun `a pause while buffering is paused - not loading`() = test { controller ->
        controller.start(listOf(a, b))
        engine.emitState(IosAudioPlayerState.Loading, checkNotNull(engine.current).id)
        engine.settle()

        controller.pause()
        engine.settle()

        controller.playbackState() shouldBe PlaybackState.Paused
        controller.bufferingFlow.value shouldBe false
    }

    @Test
    fun `a track that starts loading is loading but not buffering`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { }
        controller.play()

        controller.playbackState() shouldBe PlaybackState.Loading
        controller.bufferingFlow.value shouldBe false
    }

    @Test
    fun `a play refused between a pause and its report clears intent`() = test { controller ->
        controller.start(listOf(a, b))
        engine.acceptsPlay = false

        controller.pause()
        controller.play()
        engine.settle()

        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
        controller.currentSong shouldBe a
    }

    @Test
    fun `a paused report for the song a skip replaced is ignored`() = test { controller ->
        controller.start(listOf(a, b, c))
        controller.skipToNext()
        engine.settle()

        engine.emitState(IosAudioPlayerState.Paused, "stale")
        engine.settle()

        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `reaching the end or exhausting failures or emptying the queue clears intent - a gapless move keeps it`() = test { controller ->
        controller.start(listOf(a, b))
        engine.finishTrack()
        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe true

        controller.start(listOf(a))
        engine.finishTrack()
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused

        engine.failing += listOf(url(b), url(c))
        controller.start(listOf(a, b, c))
        controller.skipToNext()
        engine.settle()
        controller.currentSong shouldBe c
        controller.playWhenReadyFlow.value shouldBe false

        controller.start(listOf(a, b), play = false)
        controller.clearQueue()
        controller.playWhenReadyFlow.value shouldBe false
        controller.queueOperations.getSize() shouldBe 0

        controller.start(listOf(a, b))
        controller.clearQueue()
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `removing the current song keeps a playing intent and removing the rest of the queue clears it`() = test { controller ->
        controller.start(listOf(a, b, c))

        controller.removeQueueItem(controller.queueOperations.getQueue()[0])
        engine.settle()
        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe true

        val rest = controller.queueOperations.getQueue()
        controller.queueOperations.remove(rest)
        engine.settle()
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    // A replaced engine

    @Test
    fun `a skip in flight when the engine is replaced completes once the new engine has its song`() = test { controller ->
        controller.start(listOf(a, b, c), play = false)
        var result: Result<Any?>? = null
        controller.skipToNext { result = it }

        // A media-services reset: the old engine's reports never come, and the song is handed to the new one (#707).
        engine.clearCalls()
        controller.reloadEngine(0)
        result shouldBe null
        engine.calls.first() shouldBe "load song:2@0 playing"

        engine.settle()
        result?.isSuccess shouldBe true
        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a load whose completion plays still plays when the engine is replaced mid-load`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { result -> result.onSuccess { controller.play() } }

        controller.reloadEngine(0)
        engine.settle()

        controller.playWhenReadyFlow.value shouldBe true
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a replaced engine gets a paused song where it was - and nothing when nothing is loaded`() = test { controller ->
        controller.reloadEngine(0)
        engine.calls shouldBe emptyList()

        controller.start(listOf(a, b), play = false)
        controller.reloadEngine(42_000)
        engine.settle()

        engine.calls.first() shouldBe "load song:1@42000"
        controller.playWhenReadyFlow.value shouldBe false
        controller.playbackState() shouldBe PlaybackState.Paused
        controller.progressFlow.value?.position shouldBe 42_000
    }

    // Seeking while loading

    @Test
    fun `a seek while the song's stream is still resolving starts it there`() = test { controller ->
        val gate = CompletableDeferred<Unit>()
        resolveGate = gate
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { }
        controller.play()

        controller.seekTo(30_000)
        controller.progressFlow.value?.position shouldBe 30_000
        controller.playbackState() shouldBe PlaybackState.Loading

        resolveGate = null
        gate.complete(Unit)
        engine.settle()

        engine.calls.first { it.startsWith("load") } shouldBe "load song:1@30000 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
        controller.progressFlow.value?.position shouldBe 30_000
    }

    @Test
    fun `a seek while the engine is opening the song is handed to it`() = test { controller ->
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { }

        controller.seekTo(30_000)
        controller.playbackState() shouldBe PlaybackState.Loading
        engine.settle()

        engine.calls shouldContain "seek 30000"
        controller.getProgress() shouldBe 30_000
    }
}
