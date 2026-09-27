package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackProgress
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.SongPosition
import com.simplecityapps.playback.queue.RepeatMode
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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

    /** Songs on a server, whose streams open at a position (`StartTimeTicks`). */
    private val server = mutableSetOf<Long>()

    private fun url(song: Song) = "song:${song.id}"

    private fun test(block: suspend TestScope.(IosPlayerController) -> Unit): TestResult = runTest {
        val controller = IosPlayerController(
            player = engine,
            resolver = { song, startPositionMs ->
                when {
                    song.id in unresolvable -> error("No stream for ${song.name}")
                    song.id in server && startPositionMs > 0 -> IosStream("${url(song)}?from=$startPositionMs", opensAtPosition = true)
                    else -> IosStream(url(song), opensAtPosition = song.id in server)
                }
            },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
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
    fun `playing out a song moves on to the next gaplessly - and reports it ended`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b, c))

        engine.finishTrack()

        controller.currentSong shouldBe b
        ended shouldBe listOf(a)
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
        ended shouldBe listOf(b)

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
        ended shouldBe listOf(a)
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

        engine.calls.take(2) shouldBe listOf("load song:1@0", "next song:2")
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

    @Test
    fun `a next song that fails is skipped when playback reaches it`() = test { controller ->
        val failures = collect(controller.playbackFailureFlow)
        val ended = collect(controller.trackEndedFlow)
        engine.failing += url(b)
        controller.start(listOf(a, b, c))

        engine.finishTrack()

        controller.currentSong shouldBe c
        failures shouldBe listOf(b)
        ended shouldBe listOf(a)
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

        engine.calls shouldBe listOf("seek 30000", "load song:1?from=30000@0 playing", "next song:2")
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

        engine.calls shouldBe listOf("load song:1?from=10000@0 playing", "next song:2", "load song:1@0 playing", "next song:2")
        controller.progressFlow.value?.position shouldBe 0
        controller.currentSong shouldBe a
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
        engine.load(IosAudioTrack("stale", url(a), emptyMap(), 0f), null, 0, playWhenReady = false)
        engine.settle()

        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
    }
}
