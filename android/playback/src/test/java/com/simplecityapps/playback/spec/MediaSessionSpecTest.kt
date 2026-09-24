package com.simplecityapps.playback.spec

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionResult
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The media session rules in docs/testing/playback-behaviour-spec.md: what another app (Android Auto, a Bluetooth
 * headset, the notification, Assistant) sees and can do through the session, driven through a [MediaBrowser]
 * connected to it, over the real playback stack.
 */
@RunWith(RobolectricTestRunner::class)
class MediaSessionSpecTest {
    private val album = listOf(albumSong(1, track = 1), albumSong(2, track = 2), albumSong(3, track = 3))

    private val harnesses = mutableListOf<SessionHarness>()

    @After
    fun tearDown() {
        harnesses.forEach(SessionHarness::release)
    }

    @Test
    fun `RS-41 a controller's play, pause, seek and skip act as the app's own buttons do`() {
        val harness = sessionHarness()
        val playback = harness.playback.playbackOperations
        val queue = harness.playback.queueOperations
        loadPaused(harness, listOf(song(1), song(2), song(3)))
        val browser = harness.connect()

        browser.play()
        harness.playback.runUntil { playback.playbackStateFlow.value == PlaybackState.Playing }
        // Playing through the app's playback takes audio focus.
        harness.playback.audioFocus.requests shouldBeGreaterThan 0

        browser.pause()
        harness.playback.runUntil { playback.playbackStateFlow.value == PlaybackState.Paused }

        browser.seekTo(1_000)
        harness.playback.runUntil { playback.positionAnchorFlow.value.positionMs == 1_000 }

        // Next skips even with repeat-one on, as the app's own next button does.
        queue.setRepeatMode(QueueManager.RepeatMode.One)
        browser.seekToNext()
        harness.playback.runUntil { queue.queueStateFlow.value.currentPosition == 1 }

        browser.seekToPreviousMediaItem()
        harness.playback.runUntil { queue.queueStateFlow.value.currentPosition == 0 }

        // Choosing an item in the controller's queue (Android Auto's queue list) skips to it.
        browser.seekTo(2, 0)
        harness.playback.runUntil { queue.queueStateFlow.value.currentPosition == 2 }
    }

    @Test
    fun `RS-42 the browse tree lists the library's folders, an album's songs and search results`() {
        val harness = sessionHarness(songs = album + song(4), albums = listOf(albumOf(album)))
        val browser = harness.connect()

        val root = harness.await(browser.getLibraryRoot(null)).value!!
        root.mediaId shouldBe MediaIdHelper.ROOT_ID
        children(harness, browser, root.mediaId).map { it.mediaMetadata.title.toString() } shouldBe listOf("Artists", "Albums", "Playlists", "Shuffle All")

        val albums = children(harness, browser, "media:/album_root/")
        albums.map { it.mediaMetadata.title.toString() } shouldBe listOf("Blue")
        val songs = children(harness, browser, albums.single().mediaId)
        songs.map { it.mediaMetadata.title.toString() } shouldBe album.map { it.name }
        songs.forEach { it.mediaMetadata.isPlayable shouldBe true }

        harness.await(browser.search("Song2", null))
        val results = harness.await(browser.getSearchResult("Song2", 0, 100, null)).value!!
        results.map { it.mediaMetadata.title.toString() } shouldBe listOf("Song2")

        // An id that names nothing has no children.
        children(harness, browser, "media:/nothing/").shouldBeEmpty()
    }

    @Test
    fun `RS-43 playing a browsed song queues its album from that song and plays`() {
        val harness = sessionHarness(songs = album, albums = listOf(albumOf(album)))
        val queue = harness.playback.queueOperations
        val browser = harness.connect()
        val albumId = children(harness, browser, "media:/album_root/").single().mediaId
        val second = children(harness, browser, albumId)[1]

        browser.setMediaItem(MediaItem.Builder().setMediaId(second.mediaId).build())
        browser.prepare()
        browser.play()

        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }
        queue.getQueue().map { it.song } shouldBe album
        queue.queueStateFlow.value.currentItem?.song shouldBe album[1]
        // The controller sees the queue as it is.
        harness.playback.runUntil { browser.mediaItemCount == album.size && browser.currentMediaItemIndex == 1 }
        browser.currentMediaItem?.mediaMetadata?.title.toString() shouldBe album[1].name
    }

    @Test
    fun `RS-44 play with no queue loaded resumes the saved queue once it's restored`() {
        // As when a headset's play button starts the app: the saved queue is still being restored.
        val harness = sessionHarness(restored = false)
        val queue = harness.playback.queueOperations
        val saved = listOf(song(1), song(2))
        val browser = harness.connect()

        browser.play()
        harness.playback.idle()
        harness.playback.run { queue.setQueue(saved, position = 1) }
        queue.hasRestoredQueue = true

        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }
        queue.queueStateFlow.value.currentItem?.song shouldBe saved[1]
        queue.getQueue().map { it.song } shouldBe saved
    }

    @Test
    fun `RS-45 the shuffle and repeat buttons change the modes and show the ones they're in`() {
        val harness = sessionHarness()
        val queue = harness.playback.queueOperations
        loadPaused(harness, listOf(song(1), song(2), song(3)))
        val browser = harness.connect()
        browser.mediaButtonPreferences.map { it.icon } shouldBe listOf(CommandButton.ICON_SHUFFLE_OFF, CommandButton.ICON_REPEAT_OFF)

        harness.await(browser.sendCustomCommand(SessionCallback.TOGGLE_SHUFFLE, Bundle.EMPTY)).resultCode shouldBe SessionResult.RESULT_SUCCESS
        harness.playback.runUntil { queue.getShuffleMode() == QueueManager.ShuffleMode.On }

        harness.await(browser.sendCustomCommand(SessionCallback.TOGGLE_REPEAT, Bundle.EMPTY))
        queue.getRepeatMode() shouldBe QueueManager.RepeatMode.All
        harness.playback.runUntil { browser.mediaButtonPreferences.map { it.icon } == listOf(CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ALL) }

        harness.await(browser.sendCustomCommand(SessionCallback.TOGGLE_REPEAT, Bundle.EMPTY))
        harness.playback.runUntil { browser.mediaButtonPreferences.map { it.icon } == listOf(CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ONE) }
    }

    private fun sessionHarness(
        songs: List<Song> = emptyList(),
        albums: List<Album> = emptyList(),
        restored: Boolean = true
    ) = SessionHarness(songs = songs, albums = albums, restored = restored).also { harnesses += it }

    private fun children(
        harness: SessionHarness,
        browser: MediaBrowser,
        parentId: String
    ): List<MediaItem> = harness.await(browser.getChildren(parentId, 0, 100, null)).value.orEmpty()

    private fun loadPaused(
        harness: SessionHarness,
        songs: List<Song>
    ) {
        val playback = harness.playback.playbackOperations
        harness.playback.run { harness.playback.queueOperations.setQueue(songs) }
        var result: Result<Boolean>? = null
        playback.load(0) { result = it }
        harness.playback.runUntil { result != null && playback.playbackStateFlow.value == PlaybackState.Paused }
    }

    private companion object {
        fun albumSong(
            id: Long,
            track: Int
        ): Song = song(id).copy(album = "Blue", albumArtist = "Joni", track = track)

        fun albumOf(songs: List<Song>): Album = Album(
            name = "Blue",
            albumArtist = "Joni",
            artists = emptyList(),
            songCount = songs.size,
            duration = songs.sumOf { it.duration },
            year = null,
            playCount = 0,
            lastSongPlayed = null,
            lastSongCompleted = null,
            groupKey = AlbumGroupKey("blue", AlbumArtistGroupKey("joni")),
            mediaProviders = emptyList()
        )
    }
}
