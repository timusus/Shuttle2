package com.simplecityapps.shuttle.ui.actions

import android.content.IntentSender
import android.net.Uri
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.toQueueItem
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class DeleteSongsTest {

    private val songRepository = FakeSongRepository()
    private val queueOperations = FakeQueueOperations()
    private val actions = TestMediaActions(songRepository = songRepository, queueOperations = queueOperations)

    private val song = createSong(id = 1, path = "content://a")
    private val other = createSong(id = 2, path = "content://b")
    private val queue = listOf(song, other).map { it.toQueueItem(isCurrent = false) }

    init {
        queueOperations.queueStateFlow.value = QueueState(items = queue, currentItem = null, currentPosition = null)
    }

    @Test
    fun `deletes the file, then removes the song from the library and the queue`() = runTest {
        val result = actions.deleteSongs(MediaSelection.Songs(song))

        result shouldBe DeleteSongs.Result(deleted = listOf(song), failed = emptyList())
        songRepository.removed shouldBe listOf(song)
        queueOperations.removedItems shouldBe listOf(queue[0])
    }

    @Test
    fun `a file that won't delete is reported and kept in the library`() = runTest {
        actions.fileDeleter = SongFileDeleter { it.path != other.path }

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(song, other)))

        result shouldBe DeleteSongs.Result(deleted = listOf(song), failed = listOf(other))
        songRepository.removed shouldBe listOf(song)
    }

    @Test
    fun `MediaStore songs are deleted in one confirmed request, then removed from the library and the queue`() = runTest {
        val requests = mutableListOf<List<Song>>()
        actions.mediaStoreDeleter = MediaStoreSongDeleter { songs, _ ->
            requests += songs
            songs.toSet()
        }
        actions.fileDeleter = SongFileDeleter { error("MediaStore songs don't go through SAF") }
        val first = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        val second = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(first, second)))

        requests shouldBe listOf(listOf(first, second))
        result shouldBe DeleteSongs.Result(deleted = listOf(first, second), failed = emptyList())
        songRepository.removed shouldBe listOf(first, second)
        queueOperations.removedItems shouldBe queue
    }

    @Test
    fun `declining the MediaStore request changes nothing`() = runTest {
        actions.mediaStoreDeleter = MediaStoreSongDeleter { _, _ -> emptySet() }
        val mediaStore = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")

        val result = actions.deleteSongs(MediaSelection.Songs(mediaStore))

        result shouldBe DeleteSongs.Result(deleted = emptyList(), failed = listOf(mediaStore))
        songRepository.removed.shouldBeEmpty()
        queueOperations.removedItems.shouldBeEmpty()
    }

    @Test
    fun `when only some MediaStore files delete, only those songs leave the library and the queue`() = runTest {
        val first = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        val second = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")
        actions.mediaStoreDeleter = MediaStoreSongDeleter { _, _ -> setOf(second) }

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(first, second)))

        result shouldBe DeleteSongs.Result(deleted = listOf(second), failed = listOf(first))
        songRepository.removed shouldBe listOf(second)
        queueOperations.removedItems shouldBe listOf(queue[1])
    }

    @Test
    fun `a mixed selection sends MediaStore songs to the system and the rest through SAF`() = runTest {
        val mediaStoreRequests = mutableListOf<List<Song>>()
        val safDeletes = mutableListOf<Song>()
        actions.mediaStoreDeleter = MediaStoreSongDeleter { songs, _ ->
            mediaStoreRequests += songs
            songs.toSet()
        }
        actions.fileDeleter = SongFileDeleter {
            safDeletes += it
            true
        }
        val mediaStore = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")

        val result = actions.deleteSongs(MediaSelection.Songs(listOf(song, mediaStore)))

        mediaStoreRequests shouldBe listOf(listOf(mediaStore))
        safDeletes shouldBe listOf(song)
        result.deleted.toSet() shouldBe setOf(song, mediaStore)
        result.failed.shouldBeEmpty()
        songRepository.removed.toSet() shouldBe setOf(song, mediaStore)
        queueOperations.removedItems.toSet() shouldBe queue.toSet()
    }

    private val confirmations = ConfirmationHandoff<IntentSender>(pickupTimeout = 5.seconds)
    private val mediaStore = other.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "11")

    // Android 11+: the whole batch goes through the system's delete dialog
    private fun deleteThroughSystemDialog() {
        val edge = object : MediaStoreDeleteEdge {
            override fun uriFor(externalId: Long): Uri = mockk(relaxed = true)

            override fun createDeleteRequest(uris: List<Uri>): IntentSender = mockk()

            override fun hasWriteAccess() = true

            override suspend fun deleteDirect(uri: Uri): DirectDelete = error("Android 11+ deletes through the dialog")
        }
        val flow = MediaStoreDeleteFlow(edge, sdkInt = 30, confirmations = confirmations)
        actions.mediaStoreDeleter = MediaStoreSongDeleter { songs, callerActive -> flow.delete(songs, callerActive) }
    }

    // Android 10: each song's delete asks for its own confirmation, then goes through
    private fun deleteThroughPerSongPrompts() {
        val edge = object : MediaStoreDeleteEdge {
            private val uris = mutableMapOf<Long, Uri>()
            private val confirmed = mutableSetOf<Uri>()

            override fun uriFor(externalId: Long): Uri = uris.getOrPut(externalId) { mockk(relaxed = true) }

            override fun createDeleteRequest(uris: List<Uri>): IntentSender = error("Android 10 asks song by song")

            override fun hasWriteAccess() = true

            override suspend fun deleteDirect(uri: Uri): DirectDelete = if (confirmed.add(uri)) DirectDelete.NeedsConfirmation(mockk()) else DirectDelete.Deleted
        }
        val flow = MediaStoreDeleteFlow(edge, sdkInt = 29, confirmations = confirmations)
        actions.mediaStoreDeleter = MediaStoreSongDeleter { songs, callerActive -> flow.delete(songs, callerActive) }
    }

    @Test
    fun `on Android 10 the screen going away stops the prompts after the song being confirmed`() = runTest {
        deleteThroughPerSongPrompts()
        val first = song.copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        val caller = launch { actions.deleteSongs(MediaSelection.Songs(listOf(first, mediaStore))) }
        val request = confirmations.requests.first()
        confirmations.launch(request)

        caller.cancel()
        runCurrent()
        confirmations.deliver(request.token, true)
        runCurrent()

        songRepository.removed shouldBe listOf(first)
        queueOperations.removedItems shouldBe listOf(queue[0])
        withTimeoutOrNull(10.seconds) { confirmations.requests.first() } shouldBe null
    }

    @Test
    fun `accepting the system dialog removes the songs even after the screen that asked has gone`() = runTest {
        deleteThroughSystemDialog()
        val caller = launch { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }
        val request = confirmations.requests.first()
        confirmations.launch(request)

        // The user takes longer than the pickup timeout, and the activity behind the dialog is destroyed meanwhile
        advanceTimeBy(60.seconds)
        caller.cancel()
        runCurrent()
        confirmations.deliver(request.token, true)
        runCurrent()

        songRepository.removed shouldBe listOf(mediaStore)
        queueOperations.removedItems shouldBe listOf(queue[1])
    }

    @Test
    fun `declining the system dialog after the screen that asked has gone keeps the songs`() = runTest {
        deleteThroughSystemDialog()
        val caller = launch { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }
        val request = confirmations.requests.first()
        confirmations.launch(request)

        advanceTimeBy(60.seconds)
        caller.cancel()
        runCurrent()
        confirmations.deliver(request.token, false)
        runCurrent()

        songRepository.removed.shouldBeEmpty()
        queueOperations.removedItems.shouldBeEmpty()
    }

    @Test
    fun `a system dialog no screen ever shows fails the delete after the pickup timeout`() = runTest {
        deleteThroughSystemDialog()
        val result = async { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }

        advanceTimeBy(5.seconds + 1.seconds)

        result.isCompleted shouldBe true
        result.await() shouldBe DeleteSongs.Result(deleted = emptyList(), failed = listOf(mediaStore))
        songRepository.removed.shouldBeEmpty()
    }

    @Test
    fun `a system dialog its finishing host abandons keeps the songs and lets the next delete through`() = runTest {
        deleteThroughSystemDialog()
        val first = async { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }
        val firstRequest = confirmations.requests.first()
        confirmations.launch(firstRequest)

        confirmations.abandon(firstRequest.token)
        first.await().deleted.shouldBeEmpty()
        val second = async { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }
        val secondRequest = confirmations.requests.first()
        confirmations.launch(secondRequest)
        confirmations.deliver(secondRequest.token, true)

        second.await().deleted shouldBe listOf(mediaStore)
        songRepository.removed shouldBe listOf(mediaStore)
    }

    @Test
    fun `a system dialog whose answer never comes back keeps the songs and lets the next delete through after the cap`() = runTest {
        deleteThroughSystemDialog()
        val first = async { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }
        val firstRequest = confirmations.requests.first()
        confirmations.launch(firstRequest)
        val second = async { actions.deleteSongs(MediaSelection.Songs(mediaStore)) }

        advanceTimeBy(5.minutes + 1.seconds)
        first.await().deleted.shouldBeEmpty()

        // The lost answer turning up late changes nothing
        confirmations.deliver(firstRequest.token, true)
        val secondRequest = confirmations.requests.first()
        confirmations.launch(secondRequest)
        confirmations.deliver(secondRequest.token, true)

        second.await().deleted shouldBe listOf(mediaStore)
        songRepository.removed shouldBe listOf(mediaStore)
    }

    @Test
    fun `a remote song is never attempted`() = runTest {
        val attempted = mutableListOf<Long>()
        actions.fileDeleter = SongFileDeleter {
            attempted += it.id
            true
        }
        val remote = song.copy(externalId = "jellyfin-1")

        val result = actions.deleteSongs(MediaSelection.Songs(remote))

        result shouldBe DeleteSongs.Result(deleted = emptyList(), failed = listOf(remote))
        attempted.shouldBeEmpty()
        queueOperations.removedItems.shouldBeEmpty()
    }
}
