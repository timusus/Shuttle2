package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

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

        // The pages after the first are requested together, so the last was already on its way
        requests shouldBe listOf(0 to 4, 4 to 4, 8 to 2)
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

    private fun transient(status: HttpStatusCode = HttpStatusCode.ServiceUnavailable, retryAfterSeconds: Long? = null) = NetworkResult.Failure(RemoteServiceHttpError(status, retryAfterSeconds = retryAfterSeconds))

    @Test
    fun `a page that fails transiently is retried and the listing carries on`() = runTest {
        var attempts = 0
        val events = pagedFlow<Int>(pageSize = 4) { offset, _ ->
            attempts++
            when (attempts) {
                1 -> NetworkResult.Failure(NetworkError(true, IOException("reset")))
                2 -> transient(HttpStatusCode.TooManyRequests, retryAfterSeconds = 2)
                else -> NetworkResult.Success(Page(listOf(offset), totalCount = 1))
            }
        }.toList().described()

        attempts shouldBe 3
        events.last() shouldBe Event.Success(listOf(0))
    }

    @Test
    fun `a page that keeps failing fails the listing after three attempts`() = runTest {
        var attempts = 0
        val events = pagedFlow<Int>(pageSize = 4) { _, _ ->
            attempts++
            transient()
        }.toList().described()

        attempts shouldBe 3
        events.size shouldBe 1
        (events.single() is Event.Failure) shouldBe true
    }

    @Test
    fun `a client error other than 429 is not retried`() = runTest {
        var attempts = 0
        val events = pagedFlow<Int>(pageSize = 4) { _, _ ->
            attempts++
            transient(HttpStatusCode.NotFound)
        }.toList().described()

        attempts shouldBe 1
        (events.single() is Event.Failure) shouldBe true
    }

    @Test
    fun `an item repeated across pages is emitted once`() = runTest {
        // The listing shifted by one between pages, so 3 shows up twice
        val events = pagedFlow<Int>(pageSize = 4, key = { it }) { offset, _ ->
            NetworkResult.Success(Page(if (offset == 0) listOf(0, 1, 2, 3) else listOf(3, 4, 5, 6), totalCount = 7))
        }.toList().described()

        events.last() shouldBe Event.Success(listOf(0, 1, 2, 3, 4, 5, 6))
    }

    @Test
    fun `a listing that falls short of its total is emitted as incomplete`() = runTest {
        val events = pagedFlow<Int>(pageSize = 4, key = { it }) { offset, _ ->
            NetworkResult.Success(Page(if (offset == 0) listOf(0, 1, 2, 3) else listOf(3, 3), totalCount = 8))
        }.toList()

        events.described().last() shouldBe Event.Success(listOf(0, 1, 2, 3))
        (events.last() as FlowEvent.Success).complete shouldBe false
    }

    @Test
    fun `a complete listing is emitted as complete, after a short one from the same server`() = runTest {
        var short = true
        val fetchPage = server(total = 10)
        val listing =
            pagedFlow<Int>(pageSize = 4, key = { it }) { offset, limit ->
                // The first listing loses its last page's items, the next gets them all
                if (short && offset >= 8) NetworkResult.Success(Page(emptyList(), totalCount = 10)) else fetchPage(offset, limit)
            }

        (listing.toList().last() as FlowEvent.Success).complete shouldBe false
        short = false
        val complete = listing.toList().last() as FlowEvent.Success
        complete.result shouldBe (0 until 10).toList()
        complete.complete shouldBe true
    }

    @Test
    fun `a negative Retry-After retries at once`() = runTest {
        var attempts = 0
        pagedFlow<Int>(pageSize = 4) { offset, _ ->
            attempts++
            if (attempts == 1) transient(HttpStatusCode.TooManyRequests, retryAfterSeconds = -5) else NetworkResult.Success(Page(listOf(offset), totalCount = 1))
        }.toList()

        attempts shouldBe 2
        testScheduler.currentTime shouldBe 0L
    }

    @Test
    fun `a long Retry-After is capped at 30 seconds`() = runTest {
        var attempts = 0
        pagedFlow<Int>(pageSize = 4) { offset, _ ->
            attempts++
            if (attempts == 1) transient(HttpStatusCode.TooManyRequests, retryAfterSeconds = 3_600) else NetworkResult.Success(Page(listOf(offset), totalCount = 1))
        }.toList()

        attempts shouldBe 2
        testScheduler.currentTime shouldBe 30_000L
    }

    @Test
    fun `a 501 is not retried`() = runTest {
        var attempts = 0
        pagedFlow<Int>(pageSize = 4) { _, _ ->
            attempts++
            transient(HttpStatusCode.NotImplemented)
        }.toList()

        attempts shouldBe 1
    }

    @Test
    fun `after the first page - three pages are requested at once`() = runTest {
        var inFlight = 0
        var mostInFlight = 0
        val events = pagedFlow<Int>(pageSize = 2) { offset, limit ->
            inFlight++
            mostInFlight = maxOf(mostInFlight, inFlight)
            delay(100)
            inFlight--
            server(total = 12)(offset, limit)
        }.toList().described()

        mostInFlight shouldBe 3
        events.last() shouldBe Event.Success((0 until 12).toList())
        // The first page alone, then pages 2-4 together, then 5 and 6
        testScheduler.currentTime shouldBe 300L
    }

    @Test
    fun `pages that arrive out of order are kept in listing order`() = runTest {
        val events = pagedFlow<Int>(pageSize = 2) { offset, limit ->
            // Each later page comes back sooner
            delay(1_000L - offset * 100L)
            server(total = 8)(offset, limit)
        }.toList().described()

        events shouldBe listOf(progress(2, 8), progress(4, 8), progress(6, 8), progress(8, 8), Event.Success((0 until 8).toList()))
    }

    @Test
    fun `items are converted a page at a time - and duplicates dropped before converting`() = runTest {
        val converted = mutableListOf<Int>()
        val events = pagedFlow<Int, String>(pageSize = 4, key = { it }, convert = { item ->
            converted += item
            "#$item"
        }) { offset, _ ->
            NetworkResult.Success(Page(if (offset == 0) listOf(0, 1, 2, 3) else listOf(3, 4, 5), totalCount = 7))
        }.toList().described()

        converted shouldBe listOf(0, 1, 2, 3, 4, 5)
        events.last() shouldBe Event.Success(listOf("#0", "#1", "#2", "#3", "#4", "#5"))
    }
}
