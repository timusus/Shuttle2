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

/** The Shuttle Music Pro gate on server streams, and the plays a server's transcode runs under. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerPlaysTest : IosPlayerControllerTestBase() {
    // The Shuttle Music Pro gate

    @Test
    fun `a server queue restored at launch doesn't open the paywall - playing it does`() = test { controller ->
        val gate = ServerAccessGate(MutableStateFlow(Entitlement.Free(trialUsed = true)), startTrial = null)
        val streams = GatedServerStreams(gate)
        serverAccess = streams::access
        server += listOf(a.id, b.id)
        val paywalls = collect(gate.paywallRequests)
        controller.queueOperations.setQueue(listOf(a, b), null, 0)

        controller.load(skipUnloadable = false) { }
        engine.settle()

        paywalls shouldBe emptyList()
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused

        controller.play()
        engine.settle()

        paywalls.first() shouldBe PaywallSource.ServerPlayback
    }

    @Test
    fun `a refused server song opens the paywall once and stops the queue there - it doesn't skip through it`() = test { controller ->
        val gate = ServerAccessGate(MutableStateFlow(Entitlement.Free(trialUsed = true)), startTrial = null)
        val streams = GatedServerStreams(gate)
        serverAccess = streams::access
        server += listOf(a.id, b.id, c.id)
        val paywalls = collect(gate.paywallRequests)
        controller.queueOperations.setQueue(listOf(a, b, c), null, 0)

        controller.load(skipUnloadable = true) { }
        engine.settle()
        controller.play()
        engine.settle()

        paywalls shouldBe listOf(PaywallSource.ServerPlayback)
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a refused next song opens the paywall once - when it becomes current and not while the song before it plays`() = test { controller ->
        val gate = ServerAccessGate(MutableStateFlow(Entitlement.Free(trialUsed = true)), startTrial = null)
        val streams = GatedServerStreams(gate)
        serverAccess = streams::access
        server += b.id
        val paywalls = collect(gate.paywallRequests)

        controller.start(listOf(a, b, c))
        engine.settle()
        paywalls shouldBe emptyList()

        engine.finishTrack()
        engine.settle()

        paywalls shouldBe listOf(PaywallSource.ServerPlayback)
        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `a next song refused before StoreKit answers isn't kept as failed - it loads when playback reaches it`() = test { controller ->
        server += b.id
        serverAccess = { _, _ -> ServerAccess.Undecided }
        controller.start(listOf(a, b, c))
        serverAccess = { _, _ -> ServerAccess.Allowed }

        engine.finishTrack()

        controller.currentSong shouldBe b
        engine.current?.url shouldBe url(b)
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

        engine.calls shouldContain "skip to 0@0 playing"
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
        engine.calls shouldContain "skip to next playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a load gives up when nothing after the failed song loads`() = test { controller ->
        engine.failing += listOf(url(b), url(c))
        controller.start(listOf(a, b, c))
        var result: Result<Any?>? = null

        controller.skipToNext { result = it }
        engine.settle()

        result?.isFailure shouldBe true
        controller.currentSong shouldBe c
        engine.calls.last() shouldBe "stop"
        controller.playbackState() shouldBe PlaybackState.Paused
    }

    @Test
    fun `under repeat all a failed last song is skipped for the first`() = test { controller ->
        engine.failing += listOf(url(b), url(c))
        controller.start(listOf(a, b, c))
        controller.queueOperations.setRepeatMode(RepeatMode.All)

        controller.skipToNext()
        engine.settle()

        controller.currentSong shouldBe a
        engine.calls.filter { it == "skip to next playing" }.size shouldBe 2
        controller.playbackState() shouldBe PlaybackState.Playing
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
        engine.calls.first() shouldBe "skip to 0@0 playing"
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    // Plays: the session a server's transcode runs under (#722)

    @Test
    fun `under repeat one the song's play carries on into its repeat`() = test { controller ->
        controller.start(listOf(a, b))

        controller.queueOperations.setRepeatMode(RepeatMode.One)
        engine.finishTrack()

        plays.count { it.first == a.id } shouldBe 1
        endedPlays shouldBe emptyList()
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
    fun `playing the last track out ends its play as nothing follows it`() = test { controller ->
        controller.start(listOf(a))
        val playOfA = plays.single { it.first == a.id }.second
        endedPlays shouldBe emptyList()

        engine.finishTrack()

        endedPlays shouldBe listOf(playOfA)
    }

    @Test
    fun `playing the last track out under repeat all ends only its own play - the wrap-around song's play carries on`() = test { controller ->
        controller.start(listOf(a, b), position = 1)
        controller.queueOperations.setRepeatMode(RepeatMode.All)
        val playOfB = plays.single { it.first == b.id }.second
        val playOfA = plays.single { it.first == a.id }.second

        engine.finishTrack()

        controller.currentSong shouldBe a
        endedPlays shouldBe listOf(playOfB)
        endedPlays shouldNotContain playOfA
    }

    @Test
    fun `a resolve cancelled after the provider opened its play ends it`() = test { controller ->
        resolveGate = CompletableDeferred()
        controller.queueOperations.setQueue(listOf(a, b), null, 0)
        controller.load { }
        controller.play()
        val playOfA = plays.single { it.first == a.id }.second
        endedPlays shouldBe emptyList()

        controller.queueOperations.clear()

        // b's pre-warm opened a play too, still resolving behind the same gate.
        endedPlays shouldContain playOfA
        endedPlays.size shouldBe plays.size
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
    fun `a report about a track the engine has since replaced is ignored`() = test { controller ->
        controller.start(listOf(a, b, c))
        controller.skipToNext()
        engine.settle()

        // A late report for the first song, from before the skip.
        engine.emitState(IosAudioPlayerState.Paused, "${controller.queueOperations.getQueue()[0].uid}")
        engine.settle()

        controller.currentSong shouldBe b
        controller.playbackState() shouldBe PlaybackState.Playing
    }
}
