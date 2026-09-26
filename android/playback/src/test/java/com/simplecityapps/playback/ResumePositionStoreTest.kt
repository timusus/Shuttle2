package com.simplecityapps.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.simplecityapps.playback.fakes.FakeListenedPlayer
import com.simplecityapps.playback.fakes.FakeSharedPreferences
import com.simplecityapps.playback.fakes.testSong
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.toQueueEntry
import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResumePositionStoreTest {
    private val items = listOf(testSong(1).toQueueEntry().toMediaItem(), testSong(2).toQueueEntry().toMediaItem())

    private val player = FakeListenedPlayer(items)

    private val preferences = PlaybackPreferenceManager(FakeSharedPreferences(), Moshi.Builder().build())

    private var switching = false

    private val store = ResumePositionStore(player, preferences, isSwitching = { switching })

    private fun becomesCurrent(
        item: MediaItem,
        reason: Int
    ) = store.onMediaItemTransition(item, reason)

    @Test
    fun `a skip to another song drops the saved position`() {
        becomesCurrent(items[0], Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        store.savePause(30_000)

        becomesCurrent(items[1], Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        store.saved shouldBe null
    }

    @Test
    fun `the first song to become current keeps the saved position it was restored at`() {
        store.savePause(30_000)

        becomesCurrent(items[0], Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        store.saved shouldBe 30_000
    }

    @Test
    fun `playing on to the next song saves its start`() {
        becomesCurrent(items[0], Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        store.savePause(30_000)

        becomesCurrent(items[1], Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        store.onPositionDiscontinuity(positionIn(items[0]), positionIn(items[1]), Player.DISCONTINUITY_REASON_AUTO_TRANSITION)

        store.saved shouldBe 0
    }

    @Test
    fun `a song changing as playback moves to or from Cast keeps the saved position`() {
        becomesCurrent(items[0], Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        store.savePause(30_000)
        switching = true

        becomesCurrent(items[1], Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        store.saved shouldBe 30_000
    }

    @Test
    fun `playback handed back from Cast saves where the receiver was, unless it never said`() {
        player.positionMs = 42_000
        store.saveHandedBack()
        store.saved shouldBe 42_000

        player.positionMs = 0
        store.saveHandedBack()
        store.saved shouldBe 42_000
    }

    @Test
    fun `with nothing saved, a podcast resumes a little before where it was left, and music from its start`() {
        val podcast = testSong(3, path = "/podcasts/episode.mp3").copy(playbackPosition = 60_000)
        val justStarted = testSong(4, path = "/books/chapter.m4b").copy(playbackPosition = 2_000)

        store.resumePosition(podcast) shouldBe 55_000
        store.resumePosition(justStarted) shouldBe 0
        store.resumePosition(testSong(5).copy(playbackPosition = 60_000)) shouldBe 0

        store.savePause(12_000)
        store.resumePosition(podcast) shouldBe 12_000
    }

    private fun positionIn(item: MediaItem) = Player.PositionInfo(null, items.indexOf(item), item, null, items.indexOf(item), 0, 0, -1, -1)
}
