package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** A song whose chosen copy fails plays its next copy, as `ItemLoader` does on Android. */
@OptIn(ExperimentalCoroutinesApi::class)
class IosPlayerControllerCopiesTest : IosPlayerControllerTestBase() {
    private val second = song(11)
    private val otherCopy = song(12)

    private fun Song.withCopies(vararg others: Song) = copy(copies = listOf(this) + others)

    private fun missing() = sourceState.missingSongs("Shuttle")

    @Test
    fun `a copy that cannot be opened plays the next copy silently and is marked missing`() = test { controller ->
        val failures = collect(controller.playbackFailureFlow)
        unresolvable += a.id

        controller.start(listOf(a.withCopies(second), b))

        controller.currentSong?.id shouldBe second.id
        engine.current?.url shouldBe url(second)
        controller.playbackState() shouldBe PlaybackState.Playing
        failures shouldBe emptyList()
        missing() shouldBe setOf(a.id)
    }

    @Test
    fun `once every copy fails the song is skipped as before`() = test { controller ->
        unresolvable += setOf(a.id, second.id)

        controller.start(listOf(a.withCopies(second), b))

        controller.currentSong shouldBe b
        missing() shouldBe setOf(a.id, second.id)
    }

    @Test
    fun `a copy already marked missing is passed over`() = test { controller ->
        sourceState.setSongMissing("Shuttle", second.id, missing = true)
        unresolvable += a.id

        controller.start(listOf(a.withCopies(second, otherCopy), b))

        controller.currentSong?.id shouldBe otherCopy.id
    }

    @Test
    fun `a failure mid-song resumes the next copy at the same position`() = test { controller ->
        controller.start(listOf(a.withCopies(second), b))
        engine.tick(30_000)

        engine.failCurrent()

        controller.currentSong?.id shouldBe second.id
        engine.current?.url shouldBe url(second)
        engine.positionMs() shouldBe 30_000L
    }

    @Test
    fun `a marked copy that plays is no longer missing`() = test { controller ->
        sourceState.setSongMissing("Shuttle", a.id, missing = true)

        controller.start(listOf(a.withCopies(second)))

        controller.currentSong?.id shouldBe a.id
        missing() shouldBe emptySet()
    }
}
