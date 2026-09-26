package com.simplecityapps.playback.fakes

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Timeline
import androidx.media3.test.utils.StubPlayer

/**
 * A player that only reports what a test sets: a playlist of [items] at [currentIndex], where it is in the current item
 * and how long it is, which device it plays on and its speed. For the classes that listen to the player and read it
 * back, driven by calling their listener methods directly. Anything else it's asked throws.
 */
class FakeListenedPlayer(var items: List<MediaItem> = emptyList()) : StubPlayer() {
    var currentIndex = 0

    var positionMs = 0L

    var durationMs = C.TIME_UNSET

    var device: DeviceInfo = DeviceInfo.UNKNOWN

    var parameters: PlaybackParameters = PlaybackParameters.DEFAULT

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    override fun getCurrentTimeline(): Timeline = FakePlaylistTimeline(items)

    override fun getCurrentMediaItemIndex(): Int = currentIndex

    override fun getCurrentPosition(): Long = positionMs

    override fun getDuration(): Long = durationMs

    override fun getDeviceInfo(): DeviceInfo = device

    override fun getPlaybackParameters(): PlaybackParameters = parameters

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        parameters = playbackParameters
    }

    companion object {
        val REMOTE: DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    }
}
