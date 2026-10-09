package com.simplecityapps.shuttle.shared.playback

import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.queue.QueueItem
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IosPlaybackStateMappingTest {
    private fun feed(
        sent: Boolean = true,
        ready: Boolean = true,
        failed: Boolean = false
    ) = IosFeed("1-1", QueueItem(1, song(1), isCurrent = true), "play").also {
        it.sent = sent
        it.ready = ready
        it.failed = failed
    }

    private fun state(
        current: IosFeed?,
        engineState: IosAudioPlayerState,
        loadPending: Boolean = false,
        playWhenReady: Boolean = true
    ) = derivePlaybackState(current, loadPending, engineState, playWhenReady)

    @Test
    fun nothingLoadedIsPaused() {
        state(null, IosAudioPlayerState.Playing, loadPending = true) shouldBe PlaybackState.Paused
        isBuffering(null, false, IosAudioPlayerState.Loading, true) shouldBe false
    }

    @Test
    fun aPendingLoadIsLoadingWhateverTheEngineSays() {
        state(feed(), IosAudioPlayerState.Playing, loadPending = true) shouldBe PlaybackState.Loading
    }

    @Test
    fun aTrackNotYetHandedOverIsLoading() {
        state(feed(sent = false, ready = false), IosAudioPlayerState.Paused) shouldBe PlaybackState.Loading
    }

    @Test
    fun aTrackTheEngineIsOpeningIsLoadingButNotBuffering() {
        val opening = feed(ready = false)

        state(opening, IosAudioPlayerState.Loading) shouldBe PlaybackState.Loading
        isBuffering(opening, false, IosAudioPlayerState.Loading, true) shouldBe false
    }

    @Test
    fun aReadyTrackRunningDryWhilePlayingIsIntendedIsBuffering() {
        isBuffering(feed(), false, IosAudioPlayerState.Loading, true) shouldBe true
        state(feed(), IosAudioPlayerState.Loading) shouldBe PlaybackState.Loading
    }

    @Test
    fun aReadyTrackLoadingWhilePausedIsNeitherBufferingNorLoading() {
        isBuffering(feed(), false, IosAudioPlayerState.Loading, false) shouldBe false
        state(feed(), IosAudioPlayerState.Loading, playWhenReady = false) shouldBe PlaybackState.Paused
    }

    @Test
    fun aFailedTrackIsPausedAndNeverBuffering() {
        val failed = feed(ready = false, failed = true)

        state(failed, IosAudioPlayerState.Loading) shouldBe PlaybackState.Paused
        isBuffering(feed(failed = true), false, IosAudioPlayerState.Loading, true) shouldBe false
    }

    @Test
    fun aReadyTrackFollowsTheEngine() {
        state(feed(), IosAudioPlayerState.Playing) shouldBe PlaybackState.Playing
        state(feed(), IosAudioPlayerState.Paused) shouldBe PlaybackState.Paused
        state(feed(), IosAudioPlayerState.Ended) shouldBe PlaybackState.Paused
    }
}
