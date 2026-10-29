package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakePlaybackOperations
import com.simplecityapps.fakes.FakeQueueOperations
import com.simplecityapps.playback.queue.QueueItem
import com.simplecityapps.playback.queue.QueueState
import com.simplecityapps.shuttle.model.MediaProviderType
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

    @Test
    fun `with nothing playing the chosen copy starts a queue`() = runTest {
        playFromCopy(merged, emby)

        queue.lastSetQueue!!.single().id shouldBe 2
        queue.lastSetQueue!!.single().copies.map { it.id } shouldBe listOf(2L, 1L)
        playback.loadedPlayWhenReady shouldBe listOf(true)
    }

    @Test
    fun `while a queue plays the chosen copy goes next and is skipped to`() = runTest {
        val current = QueueItem(uid = 0, song = createSong(id = 9), isCurrent = true)
        queue.queueStateFlow.value = QueueState(items = listOf(current), currentItem = current, currentPosition = 0)

        val result = playFromCopy(merged, emby)

        result shouldBe PlaySongs.Result.Success
        playback.playedNext.single().copies.map { it.id } shouldBe listOf(2L, 1L)
        playback.calls shouldBe listOf("skipToNext(true)")
    }
}
