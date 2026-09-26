package com.simplecityapps.playback

import android.os.SystemClock
import androidx.media3.common.DeviceInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.simplecityapps.playback.fakes.FakeListenedPlayer
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Test

class CastHandoverTest {
    private val player = FakeListenedPlayer()

    private val switches = mutableListOf<Boolean>()

    private val handover = CastHandover(player) { remote -> switches += remote }

    @Test
    fun `a move to a Cast receiver shows in whichever event comes first, once`() {
        player.device = FakeListenedPlayer.REMOTE

        handover.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        handover.onDeviceInfoChanged(player.device)

        handover.isRemote shouldBe true
        handover.isSwitching shouldBe true
        switches shouldBe listOf(true)
    }

    @Test
    fun `the move is over once the player it moved to is ready`() {
        player.device = FakeListenedPlayer.REMOTE
        handover.onPlaybackStateChanged(Player.STATE_BUFFERING)
        handover.isSwitching shouldBe true

        handover.onPlaybackStateChanged(Player.STATE_READY)

        handover.isSwitching shouldBe false
    }

    @Test
    fun `a move that fails is over`() {
        // PlaybackException's constructor reads SystemClock.elapsedRealtime() for its timestamp.
        mockkStatic(SystemClock::class)
        try {
            every { SystemClock.elapsedRealtime() } returns 0L

            player.device = FakeListenedPlayer.REMOTE
            handover.onPlaybackStateChanged(Player.STATE_BUFFERING)

            handover.onPlayerError(PlaybackException("receiver gone", null, PlaybackException.ERROR_CODE_REMOTE_ERROR))

            handover.isSwitching shouldBe false
            handover.isRemote shouldBe true
        } finally {
            unmockkStatic(SystemClock::class)
        }
    }

    @Test
    fun `moving back to this device is seen too`() {
        player.device = FakeListenedPlayer.REMOTE
        handover.onPlaybackStateChanged(Player.STATE_READY)

        player.device = DeviceInfo.UNKNOWN
        handover.onPlaybackStateChanged(Player.STATE_BUFFERING)

        handover.isRemote shouldBe false
        switches shouldBe listOf(true, false)
    }
}
