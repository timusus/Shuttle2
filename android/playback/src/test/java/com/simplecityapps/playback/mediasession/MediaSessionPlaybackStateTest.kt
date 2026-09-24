package com.simplecityapps.playback.mediasession

import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.PositionAnchor
import com.simplecityapps.playback.R
import com.simplecityapps.playback.queue.QueueManager
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The media session's playback state is derived entirely from the position anchor, the shuffle mode and the active
 * queue item id.
 */
class MediaSessionPlaybackStateTest {
    private fun state(
        anchor: PositionAnchor = PositionAnchor(PlaybackState.Paused, positionMs = 42_000, elapsedRealtimeMs = 10_000, speed = 1f),
        shuffleMode: QueueManager.ShuffleMode = QueueManager.ShuffleMode.Off,
        activeQueueItemId: Long = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
    ) = MediaSessionPlaybackState(anchor, shuffleMode, activeQueueItemId)

    private val shuffleOnAction = SessionCustomAction(MediaSessionManager.ACTION_SHUFFLE, com.simplecityapps.core.R.string.shuffle_on, R.drawable.ic_shuffle_off_black_24dp)
    private val shuffleOffAction = SessionCustomAction(MediaSessionManager.ACTION_SHUFFLE, com.simplecityapps.core.R.string.shuffle_off, R.drawable.ic_shuffle_black_24dp)

    @Test
    fun `playing publishes the anchor's position, speed and update time, and keeps the session active`() {
        state(anchor = PositionAnchor(PlaybackState.Playing, positionMs = 42_000, elapsedRealtimeMs = 10_000, speed = 1.5f)).toPublishedPlaybackState() shouldBe
            PublishedPlaybackState(
                playback = SessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, positionMs = 42_000, speed = 1.5f, updateTimeMs = 10_000, isActive = true),
                actions = SESSION_PLAYBACK_ACTIONS,
                customActions = listOf(shuffleOnAction),
                activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
            )
    }

    @Test
    fun `paused publishes a paused state and deactivates the session`() {
        state(anchor = PositionAnchor(PlaybackState.Paused, positionMs = 5_000, elapsedRealtimeMs = 20_000, speed = 1f)).toPublishedPlaybackState().playback shouldBe
            SessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, positionMs = 5_000, speed = 1f, updateTimeMs = 20_000, isActive = false)
    }

    @Test
    fun `an anchor without a position publishes an unknown position`() {
        state(anchor = PositionAnchor(PlaybackState.Paused, positionMs = null, elapsedRealtimeMs = 0, speed = 1f)).toPublishedPlaybackState().playback.positionMs shouldBe
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
    }

    @Test
    fun `every playback state publishes the same actions`() {
        listOf(PlaybackState.Loading, PlaybackState.Playing, PlaybackState.Paused).forEach { playbackState ->
            state(anchor = PositionAnchor(playbackState, positionMs = 0, elapsedRealtimeMs = 0, speed = 1f)).toPublishedPlaybackState().actions shouldBe SESSION_PLAYBACK_ACTIONS
        }
    }

    @Test
    fun `with shuffle off the custom action offers to turn shuffle on`() {
        state(shuffleMode = QueueManager.ShuffleMode.Off).toPublishedPlaybackState().customActions shouldBe listOf(shuffleOnAction)
    }

    @Test
    fun `with shuffle on the custom action offers to turn shuffle off`() {
        state(shuffleMode = QueueManager.ShuffleMode.On).toPublishedPlaybackState().customActions shouldBe listOf(shuffleOffAction)
    }

    @Test
    fun `the active queue item id is published as is`() {
        state(activeQueueItemId = 1234L).toPublishedPlaybackState().activeQueueItemId shouldBe 1234L
    }

    @Test
    fun `shuffle and the active item don't affect the position`() {
        val anchor = PositionAnchor(PlaybackState.Playing, positionMs = 1_000, elapsedRealtimeMs = 2_000, speed = 1f)
        state(anchor = anchor, shuffleMode = QueueManager.ShuffleMode.On, activeQueueItemId = 7L).toPublishedPlaybackState().playback shouldBe
            state(anchor = anchor).toPublishedPlaybackState().playback
    }
}
