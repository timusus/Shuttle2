package com.simplecityapps.playback.chromecast

import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.spec.PlaybackHarness
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What playback does as it moves to a Cast receiver and back: the Cast player stood in for by one around the local
 * player that says which device it plays on.
 */
@RunWith(RobolectricTestRunner::class)
class CastSwitchTest {
    private lateinit var castPlayer: SwitchingPlayer

    private val harness = PlaybackHarness(activePlayer = { SwitchingPlayer(it).also { player -> castPlayer = player } })

    private val playback = harness.playbackOperations

    @After
    fun tearDown() {
        harness.release()
    }

    private fun loadPaused(positionMs: Int) {
        harness.run { harness.queueOperations.setQueue(listOf(song(1), song(2))) }
        var loaded = false
        playback.load(positionMs) { loaded = true }
        harness.runUntil { loaded && playback.playbackStateFlow.value == PlaybackState.Paused }
    }

    @Test
    fun `casting gives up audio focus and the effect session, and coming back takes them up again`() {
        loadPaused(positionMs = 0)
        playback.play()
        harness.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        val sessionId = harness.audioEffectSessionManager.sessionId
        val abandons = harness.audioFocus.abandons

        castPlayer.switchTo(remote = true)

        harness.audioFocus.abandons shouldBe abandons + 1
        harness.audioFocus.enabled shouldBe false
        harness.audioEffectSessionManager.sessionId.shouldBeNull()

        castPlayer.switchTo(remote = false)

        harness.audioFocus.enabled shouldBe true
        harness.audioEffectSessionManager.sessionId shouldBe sessionId
    }

    @Test
    fun `coming back saves the position playback came back at`() {
        loadPaused(positionMs = 1_500)
        castPlayer.switchTo(remote = true)
        harness.playbackPreferenceManager.playbackPosition = 0

        castPlayer.switchTo(remote = false)

        harness.playbackPreferenceManager.playbackPosition shouldBe 1_500
    }

    @Test
    fun `a switch at position zero never overwrites the saved position`() {
        loadPaused(positionMs = 0)
        harness.playbackPreferenceManager.playbackPosition = 1_234

        castPlayer.switchTo(remote = true)
        harness.idle()
        castPlayer.switchTo(remote = false)
        harness.idle()

        harness.playbackPreferenceManager.playbackPosition shouldBe 1_234
    }
}

/** Stands in for a Cast player: plays through [localPlayer] throughout, but says it plays on a receiver while [switchTo] says so. */
class SwitchingPlayer(localPlayer: ExoPlayer) : ForwardingPlayer(localPlayer) {
    private var remote = false

    private val listeners = mutableListOf<Player.Listener>()

    override fun getDeviceInfo(): DeviceInfo = if (remote) {
        DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    } else {
        super.getDeviceInfo()
    }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    fun switchTo(remote: Boolean) {
        this.remote = remote
        listeners.toList().forEach { it.onDeviceInfoChanged(deviceInfo) }
    }
}
