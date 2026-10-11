package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackState
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A stream that ends well short of its song's duration: a transcode's dropped connection that read as a clean end. */
class IosPlayerControllerShortEndTest : IosPlayerControllerTestBase() {
    @Test
    fun `a transcode ending short before a gapless next re-opens at the position - the queue stays`() = test { controller ->
        server += a.id
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 60_000)

        engine.calls shouldBe listOf("load song:1?from=60000@0 playing next song:2")
        controller.currentSong shouldBe a
        controller.progressFlow.value?.position shouldBe 60_000
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a transcode ending short with nothing after it re-opens at the position`() = test { controller ->
        server += a.id
        controller.start(listOf(a))

        engine.finishTrack(atMs = 60_000)

        engine.calls shouldBe listOf("load song:1?from=60000@0 playing")
        controller.currentSong shouldBe a
        controller.playbackState() shouldBe PlaybackState.Playing
    }

    @Test
    fun `a transcode ending within 5 seconds of its duration moves on`() = test { controller ->
        server += a.id
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 196_000)

        controller.currentSong shouldBe b
        engine.calls shouldBe emptyList()
    }

    @Test
    fun `a direct-play stream ending short moves on - its container gave its length`() = test { controller ->
        server += a.id
        engine.duration = 200_000
        controller.start(listOf(a, b))
        engine.tick(1_000)

        engine.finishTrack(atMs = 60_000)

        controller.currentSong shouldBe b
        engine.calls shouldBe emptyList()
    }

    @Test
    fun `a direct-play server stream of no known length ending short moves on - re-opened it would start from 0`() = test { controller ->
        server += a.id
        directPlay += a.id
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 60_000)

        controller.currentSong shouldBe b
        engine.calls shouldBe emptyList()
    }

    @Test
    fun `a local file ending short moves on`() = test { controller ->
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 60_000)

        controller.currentSong shouldBe b
    }

    @Test
    fun `a transcode that keeps ending where it was re-opened moves on after three re-opens`() = test { controller ->
        server += a.id
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 60_000)
        repeat(3) { engine.finishTrack(atMs = 0) }

        engine.calls.count { it.startsWith("load song:1?from=60000") } shouldBe 3
        controller.currentSong shouldBe b
    }

    @Test
    fun `a transcode that plays on further each re-open keeps being re-opened`() = test { controller ->
        server += a.id
        controller.start(listOf(a, b))

        engine.finishTrack(atMs = 30_000)
        engine.finishTrack(atMs = 30_000)
        engine.finishTrack(atMs = 30_000)
        engine.finishTrack(atMs = 30_000)

        engine.calls.last() shouldBe "load song:1?from=120000@0 playing next song:2"
        controller.currentSong shouldBe a
        engine.finishTrack()
        controller.currentSong shouldBe b
    }
}
