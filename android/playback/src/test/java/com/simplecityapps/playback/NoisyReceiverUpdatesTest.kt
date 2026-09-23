package com.simplecityapps.playback

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [NoiseManager] keeps its noisy receiver registered only while playback is loading or playing. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoisyReceiverUpdatesTest {
    private val playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Paused)

    private var registered = false
    private var registrations = 0

    private fun TestScope.launchUpdates() {
        backgroundScope.launchNoisyReceiverUpdates(
            playbackStateFlow = playbackState,
            context = UnconfinedTestDispatcher(testScheduler),
            register = {
                registered = true
                registrations++
            },
            unregister = { registered = false }
        )
    }

    @Test
    fun `nothing is registered while paused`() = runTest {
        launchUpdates()

        registered shouldBe false
        registrations shouldBe 0
    }

    @Test
    fun `registers straight away when playback is already playing`() = runTest {
        playbackState.value = PlaybackState.Playing
        launchUpdates()

        registered shouldBe true
    }

    @Test
    fun `registers once as playback loads then plays`() = runTest {
        launchUpdates()

        playbackState.value = PlaybackState.Loading
        playbackState.value = PlaybackState.Playing

        registered shouldBe true
        registrations shouldBe 1
    }

    @Test
    fun `unregisters when playback pauses and registers again when it resumes`() = runTest {
        launchUpdates()

        playbackState.value = PlaybackState.Playing
        playbackState.value = PlaybackState.Paused
        registered shouldBe false

        playbackState.value = PlaybackState.Playing
        registered shouldBe true
        registrations shouldBe 2
    }
}
