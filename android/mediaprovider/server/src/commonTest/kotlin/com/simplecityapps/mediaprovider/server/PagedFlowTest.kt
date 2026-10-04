package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.networking.retrofit.NetworkResult
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class PagedFlowTest {
    private val requests = mutableListOf<Pair<Int, Int>>()

    /** A server listing [total] items, which reports its total when [reportsTotal]. */
    private fun server(
        total: Int,
        reportsTotal: Boolean = true
    ): suspend (Int, Int) -> NetworkResult<Page<Int>> = { offset, limit ->
        requests += offset to limit
        val items = (offset until minOf(offset + limit, total)).toList()
        NetworkResult.Success(Page(items, if (reportsTotal) total else null))
    }

    private fun progress(
        current: Int,
        total: Int
    ) = Event.Progress(MessageProgress(ImportPhase.Fetching, Progress(current, total)))

    @Test
    fun `a listing that fits one page is one request`() = runTest {
        val events = pagedFlow(pageSize = 500, fetchPage = server(total = 3)).toList().described()

        requests shouldBe listOf(0 to 500)
        events shouldBe listOf(progress(3, 3), Event.Success(listOf(0, 1, 2)))
    }

    @Test
    fun `pages through the total - asking for no more than is left`() = runTest {
        val events = pagedFlow(pageSize = 4, fetchPage = server(total = 10)).toList().described()

        requests shouldBe listOf(0 to 4, 4 to 4, 8 to 2)
        events shouldBe listOf(
            progress(4, 10),
            progress(8, 10),
            progress(10, 10),
            Event.Success((0 until 10).toList())
        )
    }

    @Test
    fun `a short page doesn't end a listing with a total`() = runTest {
        // Jellyfin leaves some items out of a page, so offsets advance by the page size, not the items returned
        val events = pagedFlow<Int>(pageSize = 4) { offset, limit ->
            requests += offset to limit
            NetworkResult.Success(Page(if (offset == 0) listOf(0) else listOf(4, 5), totalCount = 6))
        }.toList().described()

        requests shouldBe listOf(0 to 4, 4 to 2)
        events.last() shouldBe Event.Success(listOf(0, 4, 5))
    }

    @Test
    fun `without a total - pages until an empty page`() = runTest {
        val events = pagedFlow(pageSize = 4, fetchPage = server(total = 6, reportsTotal = false)).toList().described()

        requests shouldBe listOf(0 to 4, 4 to 4, 6 to 4)
        events.last() shouldBe Event.Success((0 until 6).toList())
        events.dropLast(1).toSet() shouldBe setOf(Event.Progress(MessageProgress(ImportPhase.Fetching, null)))
    }

    @Test
    fun `a failed page fails the listing`() = runTest {
        val events = pagedFlow<Int>(pageSize = 4) { offset, limit ->
            requests += offset to limit
            if (offset == 0) NetworkResult.Success(Page(listOf(0, 1, 2, 3), totalCount = 10)) else NetworkResult.Failure(IllegalStateException("offline"))
        }.toList().described()

        requests shouldBe listOf(0 to 4, 4 to 4)
        events shouldBe listOf(progress(4, 10), Event.Failure("An unknown error occurred."))
    }

    @Test
    fun `a server that ignores the offset and reports no total ends at the repeated page`() = runTest {
        var requestCount = 0
        val events = pagedFlow<Int>(pageSize = 2) { _, _ ->
            requestCount++
            NetworkResult.Success(Page(listOf(0, 1), null))
        }.toList().described()

        requestCount shouldBe 2
        events.last() shouldBe Event.Success(listOf(0, 1))
    }
}
