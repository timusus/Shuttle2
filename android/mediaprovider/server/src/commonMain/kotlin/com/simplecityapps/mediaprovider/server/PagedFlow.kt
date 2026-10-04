package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.retrofit.error.NetworkError
import com.simplecityapps.networking.retrofit.error.RemoteServiceHttpError
import com.simplecityapps.networking.userDescription
import com.simplecityapps.shuttle.logging.Logger
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One page of a server listing. [totalCount] is the size of the whole listing, or null for a server that doesn't say. */
data class Page<T>(
    val items: List<T>,
    val totalCount: Int?
)

private val logger = Logger.tagged("PagedFlow")

/** The page size servers are asked for. */
const val DEFAULT_PAGE_SIZE = 500

/** How many times a page is requested before its failure ends the listing. */
private const val MAX_ATTEMPTS = 3
private const val BACKOFF_BASE_MS = 1_000L
private const val MAX_RETRY_AFTER_SECONDS = 30L

/** 501 and 505 are 5xx a server answers the same way every time. */
private val NOT_RETRIABLE_SERVER_ERRORS = setOf(501, 505)

/** Whether a request that failed with this is worth repeating: the server couldn't be reached, errored, or asked us to slow down. */
private fun Throwable.isTransient(): Boolean = when (this) {
    is NetworkError -> true
    is RemoteServiceHttpError -> (isServerError && httpStatusCode.value !in NOT_RETRIABLE_SERVER_ERRORS) || httpStatusCode.value == 429
    else -> false
}

/** [fetchPage] up to [MAX_ATTEMPTS] times, waiting between attempts (exponential backoff with jitter, or the server's `Retry-After`) after a transient failure. */
private suspend fun <T> retrying(fetchPage: suspend () -> NetworkResult<Page<T>>): NetworkResult<Page<T>> {
    var attempt = 1
    while (true) {
        val result = fetchPage()
        if (result !is NetworkResult.Failure || attempt >= MAX_ATTEMPTS || !result.error.isTransient()) return result
        val retryAfter = (result.error as? RemoteServiceHttpError)?.retryAfterSeconds
        val wait = if (retryAfter != null) {
            min(retryAfter, MAX_RETRY_AFTER_SECONDS).seconds.inWholeMilliseconds
        } else {
            val backoff = BACKOFF_BASE_MS shl (attempt - 1)
            backoff + Random.nextLong(backoff / 2 + 1)
        }
        logger.warn { "Page request failed (attempt $attempt of $MAX_ATTEMPTS), retrying in ${wait}ms: ${result.error.message}" }
        delay(wait)
        attempt++
    }
}

/**
 * Every item of a paged server listing, fetched a page at a time with [fetchPage] (offset, limit) and emitted as one
 * [FlowEvent.Success], after a [FlowEvent.Progress] per page with the items so far of the total. A failed page ends the flow
 * with a [FlowEvent.Failure].
 *
 * With a [Page.totalCount], each page starts [pageSize] on from the last, whatever it returned (offsets count the
 * server's items, some of which it may leave out of a page), and paging stops at the total. Without one, each page
 * starts after the items received and paging stops at the first empty page, so a server that returns fewer items
 * than asked for isn't mistaken for the end of the listing. A page identical to the one before it ends the listing too,
 * since a server that ignores the offset would otherwise repeat it forever.
 *
 * Each page is requested up to three times: a failure the server could clear (it couldn't be reached, it answered 5xx or
 * 429) waits and tries again, any other failure ends the listing at once.
 *
 * With a [key], an item already received is dropped (a server whose listing shifts mid-paging repeats items across
 * pages), and when the server gave a total and the result falls short of it, [onShortListing] is called (expected, received)
 * before the listing is emitted, so the caller can keep a sync from deleting against it: a server's total can count
 * items it never returns (Jellyfin's, with access filtering), so a short listing is still a listing, just not a
 * complete one. A listing that legitimately repeats an item (a playlist) passes no key and gets neither.
 */
fun <T> pagedFlow(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    key: ((T) -> Any)? = null,
    onShortListing: (expected: Int, received: Int) -> Unit = { _, _ -> },
    fetchPage: suspend (offset: Int, limit: Int) -> NetworkResult<Page<T>>
): Flow<FlowEvent<List<T>, MessageProgress>> = flow {
    val items = mutableListOf<T>()
    val seen = HashSet<Any>()
    var reportedTotal: Int? = null
    var offset = 0
    var limit = pageSize
    var previous: List<T>? = null
    while (true) {
        when (val result = retrying { fetchPage(offset, limit) }) {
            is NetworkResult.Success -> {
                val page = result.body
                val totalCount = page.totalCount
                if (totalCount == null && page.items.isNotEmpty() && page.items == previous) {
                    logger.error { "A page repeated the one before it; the server is ignoring the offset" }
                    emit(FlowEvent.Success(items))
                    return@flow
                }
                previous = page.items
                reportedTotal = totalCount
                val end = if (totalCount != null) offset + limit else offset + page.items.size
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, totalCount?.let { total -> Progress(min(end, total), total) })))
                if (key == null) items.addAll(page.items) else page.items.filterTo(items) { item -> seen.add(key(item)) }

                val hasMore = if (totalCount != null) end < totalCount else page.items.isNotEmpty()
                if (!hasMore) {
                    if (key != null && reportedTotal != null && items.size < reportedTotal) {
                        logger.warn { "The server reported $reportedTotal items but the listing came to ${items.size}; treating it as incomplete" }
                        onShortListing(reportedTotal, items.size)
                    }
                    emit(FlowEvent.Success(items))
                    return@flow
                }
                offset = end
                if (totalCount != null) {
                    limit = min(limit, totalCount - end)
                }
            }

            is NetworkResult.Failure -> {
                logger.error(result.error) { result.error.userDescription() }
                emit(FlowEvent.Failure(result.error.userDescription()))
                return@flow
            }
        }
    }
}
