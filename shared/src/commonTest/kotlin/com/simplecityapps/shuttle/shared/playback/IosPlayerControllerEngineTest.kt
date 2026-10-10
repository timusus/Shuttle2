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

/** The play intent kept apart from the engine's state, buffering and a replaced engine. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerEngineTest : IosPlayerControllerTestBase() {
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
    fun `a seek on a playing track is still playing - not buffering`() = test { controller ->
        controller.start(listOf(a, b))

        controller.seekTo(5_000)
        engine.settle()

        controller.playbackState() shouldBe PlaybackState.Playing
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
}
