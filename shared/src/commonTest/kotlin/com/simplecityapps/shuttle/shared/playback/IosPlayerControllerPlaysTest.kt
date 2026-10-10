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
    fun `playing a track out under repeat one ends the play it leaves but not the repeat's`() = test { controller ->
        controller.start(listOf(a))
        controller.queueOperations.setRepeatMode(RepeatMode.One)
        val repeat = plays.last { it.first == a.id }.second

        engine.finishTrack()

        endedPlays.size shouldBe 1
        endedPlays shouldNotContain repeat
    }

    @Test
    fun `a re-open cancelled while it shares the current play does not end that play`() = test { controller ->
        server += a.id
        engine.unseekable += url(a)
        controller.start(listOf(a, b))
        val playOfA = plays.single { it.first == a.id }.second
        resolveGate = CompletableDeferred()

        controller.seekTo(30_000)
        controller.seekTo(10_000)
        resolveGate?.complete(Unit)
        engine.settle()

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
}
