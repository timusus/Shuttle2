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

/** State, progress and position, including seeking a stream the engine can't seek and seeking while loading. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerPositionTest : IosPlayerControllerTestBase() {
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

        engine.calls.first() shouldBe "items [9] at 0@55000"
    }

    @Test
    fun `playing a song within its last moments restarts it`() = test { controller ->
        controller.start(listOf(a, b), play = false)
        controller.seekTo(a.duration - 100)
        engine.clearCalls()

        controller.play()

        engine.calls shouldBe listOf("seek 0", "play")
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

        engine.currentId shouldBe "${controller.queueOperations.getQueue()[0].uid}"
        engine.positionMs() shouldBe 30_000
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
