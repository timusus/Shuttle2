package com.simplecityapps.playback.spec

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionResult
import com.simplecityapps.playback.PlaybackState
import com.simplecityapps.playback.androidauto.MediaIdHelper
import com.simplecityapps.playback.mediasession.SessionCallback
import com.simplecityapps.playback.persistence.NowPlayingSnapshot
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.spec.PlaybackHarness.Companion.song
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtistGroupKey
import com.simplecityapps.shuttle.model.AlbumGroupKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.PlaylistSongSortOrder
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
    fun `RS-64 the resumption controls get the saved song straight away, and the restored queue once it's there`() {
        // As when the system builds its resumption controls, after a reboot or with S2 not running.
        val harness = sessionHarness(restored = false)
        val queue = harness.playback.queueOperations
        val saved = listOf(song(1), song(2))
        harness.playback.playbackPreferenceManager.nowPlaying = NowPlayingSnapshot.of(saved[1])
        harness.playback.playbackPreferenceManager.playbackPosition = 30_000
        harness.connect()
        val controller = harness.session.connectedControllers.first()

        val offered = harness.await(harness.callback.onPlaybackResumption(harness.session, controller, false))
        offered.mediaItems.map { it.mediaId } shouldBe listOf(saved[1].id.toString())
        offered.mediaItems.single().mediaMetadata.title shouldBe saved[1].name
        offered.startPositionMs shouldBe 30_000

        // To play, it waits for the restored queue.
        val resumed = harness.callback.onPlaybackResumption(harness.session, controller, true)
        harness.playback.idle()
        resumed.isDone shouldBe false
        harness.playback.run { queue.setQueue(saved, position = 1) }
        queue.hasRestoredQueue = true
        harness.await(resumed).mediaItems.size shouldBe 2

        // Once restored, the player's own items answer either way.
        harness.await(harness.callback.onPlaybackResumption(harness.session, controller, false)).mediaItems.size shouldBe 2
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

    @Test
    fun `RS-46 a voice search plays what it finds, and adding one adds the songs it names`() {
        val songs = listOf(song(1), song(2), song(3))
        val harness = sessionHarness(songs = songs)
        val queue = harness.playback.queueOperations
        val browser = harness.connect()

        playRequest(harness, browser, searchItem("Song2"))
        queue.getQueue().map { it.song } shouldBe listOf(songs[1])

        // Adding a search or a file to the queue adds the songs they name, as playing them would.
        browser.addMediaItem(searchItem("Song3"))
        harness.playback.runUntil { queue.getQueue().size == 2 }
        browser.addMediaItem(MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(songs[0].path)).build()).build())
        harness.playback.runUntil { queue.getQueue().size == 3 }
        queue.getQueue().map { it.song } shouldBe listOf(songs[1], songs[2], songs[0])
    }

    @Test
    fun `RS-60 a voice search focused on an artist, album, song, genre or playlist plays the closest match`() {
        val blue = listOf(albumSong(1, track = 1), albumSong(2, track = 2), albumSong(3, track = 3)).map { it.copy(genres = listOf("Folk")) }
        val other = song(4).copy(name = "Harvest Moon", albumArtist = "Neil Young", album = "Harvest Moon", genres = listOf("Rock"))
        val playlist = Playlist(id = 1, name = "Sunday Morning", songCount = 2, duration = 0, sortOrder = PlaylistSongSortOrder.Position, mediaProvider = MediaProviderType.Shuttle, externalId = null)
        val harness = SessionHarness(songs = blue + other, playlists = mapOf(playlist to listOf(other, blue[0]))).also { harnesses += it }
        val queue = harness.playback.queueOperations
        val browser = harness.connect()

        playRequest(harness, browser, searchItem("joni", MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE, MediaStore.EXTRA_MEDIA_ARTIST to "Joni"))
        queue.getQueue().map { it.song } shouldBe blue

        playRequest(harness, browser, searchItem("harvest moon", MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE, MediaStore.EXTRA_MEDIA_ALBUM to "Harvest Moon"))
        queue.getQueue().map { it.song } shouldBe listOf(other)

        // A song plays with the rest of its album after it; a title heard differently finds the closest.
        playRequest(harness, browser, searchItem("song 2", MediaStore.Audio.Media.ENTRY_CONTENT_TYPE, MediaStore.EXTRA_MEDIA_TITLE to "Song 2"))
        queue.getQueue().map { it.song } shouldBe blue
        queue.queueStateFlow.value.currentItem?.song shouldBe blue[1]

        playRequest(harness, browser, searchItem("folk", MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE, MediaStore.EXTRA_MEDIA_GENRE to "Folk"))
        queue.getQueue().map { it.song } shouldBe blue

        playRequest(harness, browser, searchItem("sunday morning", MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE, MediaStore.EXTRA_MEDIA_PLAYLIST to "Sunday Morning"))
        queue.getQueue().map { it.song } shouldBe listOf(other, blue[0])

        // Unstructured: the words alone.
        playRequest(harness, browser, searchItem("Neil Young"))
        queue.getQueue().map { it.song } shouldBe listOf(other)
    }

    @Test
    fun `RS-61 a voice search for nothing in particular resumes the queue, or shuffles the library when there's none`() {
        val songs = (1L..8L).map { song(it) }
        val harness = sessionHarness(songs = songs)
        val queue = harness.playback.queueOperations
        val browser = harness.connect()

        // "Play music" with no queue: every song, shuffled.
        playRequest(harness, browser, searchItem("  "))
        queue.getQueue().map { it.song }.toSet() shouldBe songs.toSet()

        // With a queue: it plays as it is, from where it was, whether the search is blank or names nothing at all.
        harness.playback.run { queue.setQueue(songs.take(3), position = 1) }
        playRequest(harness, browser, searchItem(""))
        queue.getQueue().map { it.song } shouldBe songs.take(3)
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[1]
        playRequest(harness, browser, MediaItem.Builder().build())
        queue.getQueue().map { it.song } shouldBe songs.take(3)
        queue.queueStateFlow.value.currentItem?.song shouldBe songs[1]
    }

    @Test
    fun `RS-47 an app that isn't trusted can play and control playback but can't browse or change the queue`() {
        val songs = listOf(song(1), song(2), song(3))
        val harness = sessionHarness(songs = songs, trusted = false)
        val queue = harness.playback.queueOperations
        val browser = harness.connect()

        val root = harness.await(browser.getLibraryRoot(null)).value!!
        children(harness, browser, root.mediaId).shouldBeEmpty()
        browser.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) shouldBe false
        browser.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM) shouldBe true
        browser.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) shouldBe true

        playRequest(harness, browser, searchItem("Song2"))
        queue.getQueue().map { it.song } shouldBe listOf(songs[1])

        browser.clearMediaItems()
        browser.addMediaItem(searchItem("Song3"))
        harness.playback.idle()
        queue.getQueue().map { it.song } shouldBe listOf(songs[1])

        // A trusted controller (the system, Android Auto) can.
        val trusted = sessionHarness(songs = songs)
        trusted.playback.run { trusted.playback.queueOperations.setQueue(songs) }
        trusted.connect().clearMediaItems()
        trusted.playback.runUntil { trusted.playback.queueOperations.getQueue().isEmpty() }
    }

    @Test
    fun `RS-57 a file opened from another app that's in the library plays as its library song, on its own`() {
        val path = checkNotNull(Uri.parse(PlaybackHarness.resourceUri(PlaybackHarness.TONE_2S)).path)
        // The same file under both local providers (#420), MediaStore's row first in the library.
        val mediaStoreRow = song(1).copy(path = path, mediaProvider = MediaProviderType.MediaStore)
        val shuttleRow = song(2).copy(path = path, mediaProvider = MediaProviderType.Shuttle)
        val harness = sessionHarness(songs = listOf(mediaStoreRow, shuttleRow, song(3), song(4)))
        val queue = harness.playback.queueOperations
        loadPaused(harness, listOf(song(3), song(4)))

        harness.playRequests.playFromUri(Uri.parse("file://$path"), "audio/wav")
        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }

        queue.getQueue().map { it.song } shouldBe listOf(shuttleRow)
    }

    @Test
    fun `RS-58 a file opened from another app that isn't in the library plays on its own, outside the library`() {
        val uri = PlaybackHarness.resourceUri(PlaybackHarness.TONE_3S)
        val harness = sessionHarness(songs = listOf(song(1), song(2)))
        val queue = harness.playback.queueOperations
        loadPaused(harness, listOf(song(1), song(2)))

        harness.playRequests.playFromUri(Uri.parse(uri), "audio/wav")
        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }

        val opened = queue.getQueue().map { it.song }.single()
        opened.path shouldBe uri
        opened.isInLibrary shouldBe false
        opened.name shouldBe "tone-3s"
    }

    private fun sessionHarness(
        songs: List<Song> = emptyList(),
        albums: List<Album> = emptyList(),
        restored: Boolean = true,
        trusted: Boolean = true
    ) = SessionHarness(songs = songs, albums = albums, restored = restored, trusted = trusted).also { harnesses += it }

    /** Asks the session to play [item], as a voice search or another app does, and waits for it to play. */
    private fun playRequest(
        harness: SessionHarness,
        browser: MediaBrowser,
        item: MediaItem
    ) {
        browser.setMediaItem(item)
        browser.prepare()
        browser.play()
        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Playing }
        browser.pause()
        harness.playback.runUntil { harness.playback.playbackOperations.playbackStateFlow.value == PlaybackState.Paused }
    }

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
        fun searchItem(query: String): MediaItem = MediaItem.Builder().setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(query).build()).build()

        /** A voice search as Assistant parses one: the words, the kind of thing they name ([focus]), and its [parts]. */
        fun searchItem(
            query: String,
            focus: String,
            vararg parts: Pair<String, String>
        ): MediaItem = MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery(query)
                    .setExtras(bundleOf(MediaStore.EXTRA_MEDIA_FOCUS to focus, *parts))
                    .build()
            )
            .build()

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
