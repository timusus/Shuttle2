package com.simplecityapps.playback.spec

import android.media.AudioManager
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_3S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The audio focus and headphone rules of the behaviour spec (docs/testing/playback-behaviour-spec.md): other apps
 * taking focus, as the platform tells the player, and headphones being unplugged.
 */
@RunWith(RobolectricTestRunner::class)
class AudioFocusSpecTest {
    private val harness = PlaybackHarness()
    private val playback = harness.playbackOperations

    @After
    fun tearDown() {
        harness.release()
    }

    @Test
    fun `RS-50 a short interruption holds playback paused, and it plays on when the interruption ends`() {
        startPlaying(listOf(song(1, file = TONE_3S)))

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_GAIN)

        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
        val position = playback.getProgress() ?: 0
        harness.runUntil { (playback.getProgress() ?: 0) > position }
    }

    @Test
    fun `RS-50 pausing during a short interruption keeps playback paused after it ends`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        val abandons = harness.audioFocus.abandons

        harness.run { playback.pause() }

        // Pausing gives focus up, so the end of the interruption can't reach the player to start it again.
        harness.audioFocus.abandons shouldBeGreaterThan abandons
        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    @Test
    fun `RS-51 another app that lets playback duck plays over it without pausing it`() {
        startPlaying(listOf(song(1, file = TONE_3S)))

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        playback.playbackStateFlow.value shouldBe PlaybackState.Playing
        val position = playback.getProgress() ?: 0
        harness.runUntil { (playback.getProgress() ?: 0) > position }
    }

    @Test
    fun `RS-52 another app taking focus for good pauses playback`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        val abandons = harness.audioFocus.abandons

        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_LOSS)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.audioFocus.abandons shouldBeGreaterThan abandons
    }

    @Test
    fun `RS-53 unplugging headphones pauses playback and gives up audio focus`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        val abandons = harness.audioFocus.abandons

        harness.unplugHeadphones()

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.audioFocus.abandons shouldBeGreaterThan abandons
    }

    private fun startPlaying(songs: List<Song>) {
        harness.run { playback.addToQueue(songs) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
    }
}
