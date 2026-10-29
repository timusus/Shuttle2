package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.playback.queue.ShuffleMode
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class PlayFromCopyTest {
    private val queue = FakeQueueOperations()
    private val playback = FakePlaybackOperations()
    private val playFromCopy = PlayFromCopy(queue, playback, PlaySongs(queue, playback))

    private val local = createSong(id = 1, mediaProvider = MediaProviderType.Shuttle)
    private val emby = createSong(id = 2, mediaProvider = MediaProviderType.Emby, path = "emby://2")
    private val merged = local.copy(copies = listOf(local, emby))

    private fun item(uid: Long, song: Song, current: Boolean = false) = QueueItem(uid = uid, song = song, isCurrent = current)

    private fun queueOf(vararg items: QueueItem, current: QueueItem? = items.firstOrNull { it.isCurrent }) {
        queue.queueStateFlow.value = QueueState(items = items.toList(), currentItem = current, currentPosition = current?.let { items.indexOf(it) })
    }

    // Where the player puts the song in the presented queue: not always right after the current item, as when shuffled.
    private fun insertAt(index: Int, uid: Long): (List<Song>) -> Unit = { songs ->
        val state = queue.queueStateFlow.value
        val items = state.items.toMutableList().apply { add(index.coerceAtMost(size), item(uid, songs.single())) }
        queue.queueStateFlow.value = state.copy(items = items, currentPosition = state.currentItem?.let { items.indexOf(it) })
    }

    @Test
    fun `with nothing playing the chosen copy starts a queue`() = runTest {
        playFromCopy(merged, emby)

        queue.lastSetQueue!!.single().id shouldBe 2
        queue.lastSetQueue!!.single().copies.map { it.id } shouldBe listOf(2L, 1L)
        playback.loadedPlayWhenReady shouldBe listOf(true)
    }

    @Test
    fun `while another song plays the chosen copy is queued after it and skipped to`() = runTest {
        val current = item(0, createSong(id = 9), current = true)
        queueOf(current, item(1, createSong(id = 8)))
        playback.onPlayNext = insertAt(1, uid = 10)

        val result = playFromCopy(merged, emby)

        result shouldBe PlaySongs.Result.Success
        playback.playedNext.single().copies.map { it.id } shouldBe listOf(2L, 1L)
        playback.calls shouldBe listOf("skipTo(1)")
    }

    @Test
    fun `shuffled the inserted copy is skipped to by its place in the queue`() = runTest {
        queue.shuffleModeFlow.value = ShuffleMode.On
        val current = item(0, createSong(id = 9), current = true)
        queueOf(current, item(1, createSong(id = 8)), item(2, createSong(id = 7)))
        playback.onPlayNext = insertAt(2, uid = 10)

        playFromCopy(merged, emby)

        playback.calls shouldBe listOf("skipTo(2)")
    }

    @Test
    fun `when the song is already current the chosen copy replaces it`() = runTest {
        val current = item(0, local, current = true)
        queueOf(current, item(1, createSong(id = 8)))
        playback.onPlayNext = insertAt(1, uid = 10)

        playFromCopy(merged, emby)

        playback.calls shouldBe listOf("skipTo(1)", "removeQueueItem(0)")
    }

    @Test
    fun `when another copy of the song is current it is replaced too`() = runTest {
        val current = item(0, emby, current = true)
        queueOf(current)
        playback.onPlayNext = insertAt(1, uid = 10)

        playFromCopy(merged, local)

        playback.calls shouldBe listOf("skipTo(1)", "removeQueueItem(0)")
    }

    @Test
    fun `a queue with no current item is kept and the copy goes on its end`() = runTest {
        queueOf(item(0, createSong(id = 8)), item(1, createSong(id = 7)), current = null)
        playback.onAddToQueue = insertAt(2, uid = 10)

        val result = playFromCopy(merged, emby)

        result shouldBe PlaySongs.Result.Success
        queue.lastSetQueue shouldBe null
        playback.addedToQueue.single().id shouldBe 2
        playback.calls shouldBe listOf("skipTo(2)")
    }

    @Test
    fun `a copy that never reaches the queue is a failure`() = runTest {
        queueOf(item(0, createSong(id = 9), current = true))

        val result = playFromCopy(merged, emby)

        result shouldBe PlaySongs.Result.Failure(null)
        playback.calls shouldBe emptyList()
    }
}
