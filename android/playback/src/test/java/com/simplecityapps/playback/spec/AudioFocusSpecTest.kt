package com.simplecityapps.playback.spec

import android.media.AudioManager
import android.os.Build
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_1S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.TONE_3S
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.unreadableSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    fun `RS-04 playing out the queue pauses at its end, keeping audio focus as a pause does`() {
        val ended = harness.record(playback.trackEndedFlow)
        startPlaying(listOf(song(1, file = TONE_1S)))

        harness.runUntil { ended.isNotEmpty() }
        harness.idle()

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.audioFocus.abandons shouldBe 0
    }

    @Test
    fun `RS-04 a song failing while playing stops playback and gives up audio focus`() {
        val failures = harness.record(playback.playbackFailureFlow)
        startPlaying(listOf(song(1, file = TONE_1S), unreadableSong(2)))

        harness.runUntil { failures.isNotEmpty() }
        harness.idle()

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.audioFocus.abandons shouldBe 1
    }

    @Test
    fun `RS-04 clearing the queue while paused gives up audio focus`() {
        startPlaying(listOf(song(1), song(2)))
        harness.run { playback.pause() }

        harness.run { playback.clearQueue() }

        harness.queueOperations.queueStateFlow.value.items.shouldBeEmpty()
        harness.audioFocus.abandons shouldBe 1
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

        harness.run { playback.pause() }
        harness.changeAudioFocus(AudioManager.AUDIOFOCUS_GAIN)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
        harness.appPlayer.isPlaying shouldBe false
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
    fun `RS-53 unplugging headphones pauses playback, keeping audio focus`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        val abandons = harness.audioFocus.abandons

        harness.unplugHeadphones()

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.audioFocus.abandons shouldBe abandons
    }

    @Test
    fun `RS-54 a play during a call waits, paused, and playback starts when the call ends`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        val requests = harness.audioFocus.requests
        harness.setAudioMode(AudioManager.MODE_IN_CALL)

        harness.run { playback.play() }

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
        harness.audioFocus.requests shouldBe requests

        harness.setAudioMode(AudioManager.MODE_NORMAL)

        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        val position = playback.getProgress() ?: 0
        harness.runUntil { (playback.getProgress() ?: 0) > position }
    }

    @Test
    fun `RS-54 a play while a call rings, is screened or redirected, or a VoIP call is on, waits too`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        val modes =
            listOf(
                AudioManager.MODE_RINGTONE,
                AudioManager.MODE_IN_CALL,
                AudioManager.MODE_IN_COMMUNICATION,
                AudioManager.MODE_CALL_SCREENING,
                AudioManager.MODE_CALL_REDIRECT,
                AudioManager.MODE_COMMUNICATION_REDIRECT
            )

        modes.forEach { mode ->
            harness.setAudioMode(mode)
            harness.run { playback.play() }

            withClue("mode $mode") { harness.appPlayer.playWhenReady shouldBe false }
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `RS-54 below API 31, where the end of a call can't be seen, a play during a call is dropped`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        harness.setAudioMode(AudioManager.MODE_IN_CALL)

        harness.run { playback.play() }
        harness.setAudioMode(AudioManager.MODE_NORMAL)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    @Test
    fun `RS-54 pausing during the call drops the waiting play`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        harness.setAudioMode(AudioManager.MODE_IN_CALL)
        harness.run { playback.play() }

        harness.run { playback.pause() }
        harness.setAudioMode(AudioManager.MODE_NORMAL)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    @Test
    fun `RS-54 a queue change during the call drops the waiting play`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        harness.setAudioMode(AudioManager.MODE_IN_CALL)
        harness.run { playback.play() }

        harness.run { playback.addToQueue(listOf(song(2))) }
        harness.setAudioMode(AudioManager.MODE_NORMAL)

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    @Test
    fun `RS-54 toggling playback during a call, as a widget or headset button does, waits too`() {
        startPlaying(listOf(song(1, file = TONE_3S)))
        harness.run { playback.pause() }
        harness.setAudioMode(AudioManager.MODE_IN_CALL)

        harness.run { playback.togglePlayback() }

        playback.playbackStateFlow.value shouldBe PlaybackState.Paused
        harness.appPlayer.playWhenReady shouldBe false
    }

    private fun startPlaying(songs: List<Song>) {
        harness.run { playback.addToQueue(songs) }
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
    }
}
