package com.simplecityapps.shuttle.ui.actions

import android.content.IntentSender
import android.net.Uri
import com.simplecityapps.createSong
import com.simplecityapps.shuttle.model.Song
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaStoreDeleteFlowTest {

    private val songs = (1L..3L).map { createSong(id = it).copy(externalId = it.toString()) }
    private val confirmations = ConfirmationHandoff<IntentSender>()
    private val uris = (1L..3L).associateWith { mockk<Uri>(relaxed = true) }

    private class FakeEdge(private val uris: Map<Long, Uri>) : MediaStoreDeleteEdge {
        val directResults = mutableMapOf<Uri, ArrayDeque<DirectDelete>>()
        val directCalls = mutableListOf<Uri>()
        var deleteRequests = 0

        override fun uriFor(externalId: Long) = uris.getValue(externalId)

        override fun createDeleteRequest(uris: List<Uri>): IntentSender {
            deleteRequests++
            return mockk()
        }

        override fun hasWriteAccess() = true

        override suspend fun deleteDirect(uri: Uri): DirectDelete {
            directCalls += uri
            return directResults.getValue(uri).removeFirst()
        }
    }

    private val edge = FakeEdge(uris)

    private fun CoroutineScope.hostAnswering(vararg answers: Boolean) = launch {
        for (answer in answers) {
            val request = confirmations.requests.first()
            confirmations.launch(request)
            confirmations.deliver(request.token, answer)
        }
    }

    private fun flow(sdkInt: Int) = MediaStoreDeleteFlow(edge, sdkInt, confirmations)

    private fun script(song: Song, vararg results: DirectDelete) {
        edge.directResults[uris.getValue(song.externalId!!.toLong())] = ArrayDeque(results.toList())
    }

    @Test
    fun `api 30 asks once for the whole batch`() = runTest {
        hostAnswering(true)

        flow(30).delete(songs) shouldBe songs.toSet()

        edge.deleteRequests shouldBe 1
    }

    @Test
    fun `api 30 deletes nothing when the batch is declined`() = runTest {
        hostAnswering(false)

        flow(30).delete(songs) shouldBe emptySet()
    }

    @Test
    fun `api 29 stops at the first declined prompt and keeps earlier deletes`() = runTest {
        script(songs[0], DirectDelete.Deleted)
        script(songs[1], DirectDelete.NeedsConfirmation(mockk()))
        script(songs[2], DirectDelete.NeedsConfirmation(mockk()))
        hostAnswering(false)

        flow(29).delete(songs) shouldBe setOf(songs[0])

        edge.directCalls.size shouldBe 2
    }

    @Test
    fun `api 29 carries on after a failed delete`() = runTest {
        script(songs[0], DirectDelete.Failed)
        script(songs[1], DirectDelete.Deleted)
        script(songs[2], DirectDelete.Deleted)

        flow(29).delete(songs) shouldBe setOf(songs[1], songs[2])
    }

    @Test
    fun `api 29 retries a song after its prompt is accepted`() = runTest {
        script(songs[0], DirectDelete.NeedsConfirmation(mockk()), DirectDelete.Deleted)
        script(songs[1], DirectDelete.Failed)
        script(songs[2], DirectDelete.Deleted)
        hostAnswering(true)

        flow(29).delete(songs) shouldBe setOf(songs[0], songs[2])
    }
}
