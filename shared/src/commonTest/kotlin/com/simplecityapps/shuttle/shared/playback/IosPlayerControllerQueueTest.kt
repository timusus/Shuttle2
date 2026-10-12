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
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Playing through the queue: pausing at the end of a song, skipping, repeat, shuffle, queue edits and failed loads. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerQueueTest : IosPlayerControllerTestBase() {
    // Pausing at the end of a song (the sleep timer's "end of song", #953)

    @Test
    fun `pausing at the end of a song pauses there - with the next song current at its start`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b, c))

        val wait = pauseAtEnd(controller)
        engine.calls shouldBe listOf("pause at end true")
        engine.finishTrack()

        wait.isCompleted shouldBe true
        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Paused
        controller.progressFlow.value?.position shouldBe 0
        ended.map { it.song } shouldBe listOf(a)
        engine.calls shouldBe listOf("pause at end true", "pause at end false")
    }

    @Test
    fun `cancelling a pause at the end turns it off - and the song plays on into the next`() = test { controller ->
        controller.start(listOf(a, b, c))

        val wait = pauseAtEnd(controller)
        wait.cancel()
        engine.finishTrack()

        engine.calls.take(2) shouldBe listOf("pause at end true", "pause at end false")
        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `playing after a pause at the end starts the next song - not the finished one again`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        engine.duration = a.duration.toLong()
        controller.start(listOf(a, b, c))
        pauseAtEnd(controller)
        engine.finishTrack()
        engine.clearCalls()

        controller.play()
        engine.settle()

        engine.calls shouldBe listOf("play")
        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
        ended.map { it.song } shouldBe listOf(a)
    }

    @Test
    fun `playing after a pause at the end before the next song resolved plays it once it has`() = test { controller ->
        controller.start(listOf(a))
        val gate = CompletableDeferred<Unit>()
        resolveGate = gate
        controller.addToQueue(listOf(b))
        pauseAtEnd(controller)
        engine.finishTrack()
        controller.currentSong shouldBe b
        controller.playWhenReadyFlow.value shouldBe false
        engine.clearCalls()

        controller.play()
        gate.complete(Unit)
        engine.settle()

        engine.calls shouldBe listOf("play")
        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a pause at the end waits out the end of the queue`() = test { controller ->
        controller.start(listOf(a))

        val wait = pauseAtEnd(controller)
        engine.finishTrack()

        wait.isCompleted shouldBe true
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    // Playing through the queue

    @Test
    fun `loading the queue hands the player its order - its first song paused`() = test { controller ->
        var result: Result<Boolean>? = null
        controller.queueOperations.setQueue(listOf(a, b, c), null, 0)

        controller.load { result = it }
        controller.playbackState() shouldBe PlaybackState.Loading
        engine.settle()

        result shouldBe Result.success(true)
        engine.calls shouldBe listOf("items [1, 2, 3] at 0@0")
        engine.next?.url shouldBe url(b)
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
        engine.calls shouldBe emptyList()
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
        engine.calls.first() shouldBe "skip to 1@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing

        controller.skipToPrev()
        engine.settle()
        controller.currentSong shouldBe a
    }

    @Test
    fun `skipping onto the next moves the player there - without resolving it again`() = test { controller ->
        controller.start(listOf(a, b, c))
        val next = checkNotNull(engine.next)
        resolved.clear()

        controller.skipToNext()
        engine.settle()

        controller.currentSong shouldBe b
        engine.calls shouldBe listOf("skip to 1@0 playing")
        engine.current shouldBe next
        resolved shouldBe emptyList()
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `skipping past the next pre-warms the songs after the one skipped to`() = test { controller ->
        controller.start(listOf(a, b, c, d))
        resolved.clear()

        controller.skipTo(2)
        engine.settle()

        controller.currentSong shouldBe c
        resolved shouldBe listOf(d.id)
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
        engine.calls.first() shouldBe "skip to 1@0 playing"
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
    fun `with repeat one - the same song plays again`() = test { controller ->
        val ended = collect(controller.trackEndedFlow)
        controller.start(listOf(a, b))
        val uid = controller.queueOperations.getCurrentItem()?.uid

        controller.queueOperations.setRepeatMode(RepeatMode.One)
        engine.next shouldBe engine.current

        engine.finishTrack()
        controller.queueOperations.getCurrentItem()?.uid shouldBe uid
        ended.map { it.song } shouldBe listOf(a)
        controller.playbackState() shouldBe PlaybackState.Playing
        engine.next?.url shouldBe url(a)

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
        engine.calls.first { it.startsWith("items") } shouldBe "items [2, 3] at 0@0 playing"
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
        engine.calls.first { it.startsWith("items") } shouldBe "items [1] at 0@0"
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

        engine.calls.first { it.startsWith("items") } shouldBe "items [1, 2] at 0@0 playing"
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
    fun `new song data for the next song keeps the player's order`() = test { controller ->
        controller.start(listOf(a, b))

        controller.queueOperations.updateSongs(listOf(b.copy(name = "Renamed")))

        engine.calls shouldBe listOf("replace [1, 2] at 0")
        controller.currentSong shouldBe a
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
        engine.calls shouldContain "skip to next playing"
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
}
