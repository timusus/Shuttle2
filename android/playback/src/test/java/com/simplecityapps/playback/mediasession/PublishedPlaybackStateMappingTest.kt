package com.simplecityapps.playback.mediasession

import android.support.v4.media.session.PlaybackStateCompat
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A [PublishedPlaybackState] maps field for field onto the [PlaybackStateCompat] the session publishes.
 */
@RunWith(RobolectricTestRunner::class)
class PublishedPlaybackStateMappingTest {
    @Test
    fun `every field is carried onto the playback state`() {
        val publishedState =
            PublishedPlaybackState(
                playback = SessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, positionMs = 42_000, speed = 1.5f, updateTimeMs = 10_000, isActive = true),
                actions = SESSION_PLAYBACK_ACTIONS,
                customActions = listOf(SessionCustomAction("action", nameRes = 1, iconRes = 2)),
                activeQueueItemId = 1234L
            )

        val playbackState = publishedState.toPlaybackStateCompat { resId -> "string $resId" }

        playbackState.state shouldBe PlaybackStateCompat.STATE_PLAYING
        playbackState.position shouldBe 42_000L
        playbackState.playbackSpeed shouldBe 1.5f
        playbackState.lastPositionUpdateTime shouldBe 10_000L
        playbackState.actions shouldBe SESSION_PLAYBACK_ACTIONS
        playbackState.activeQueueItemId shouldBe 1234L
        playbackState.customActions.map { customAction -> Triple(customAction.action, customAction.name.toString(), customAction.icon) } shouldBe
            listOf(Triple("action", "string 1", 2))
    }
}
