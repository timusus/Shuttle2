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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
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
            retryAfter.coerceIn(0, MAX_RETRY_AFTER_SECONDS).seconds.inWholeMilliseconds
        } else {
            val backoff = BACKOFF_BASE_MS shl (attempt - 1)
            backoff + Random.nextLong(backoff / 2 + 1)
        }
        logger.warn { "Page request failed (attempt $attempt of $MAX_ATTEMPTS), retrying in ${wait}ms: ${result.error.message}" }
        delay(wait)
        attempt++
    }
}

/** How many pages of a listing with a total are requested at once, after the first. */
private const val CONCURRENT_PAGES = 3

/**
 * Every item of a paged server listing, fetched a page at a time with [fetchPage] (offset, limit), converted a page at a
 * time with [convert] and emitted as one [FlowEvent.Success], after a [FlowEvent.Progress] per page with the items so far
 * of the total. A failed page ends the flow with a [FlowEvent.Failure].
 *
 * With a [Page.totalCount], the first page's total plans the rest: each page starts [pageSize] on from the last, whatever
 * it returned (offsets count the server's items, some of which it may leave out of a page), up to the total, and up to
 * [CONCURRENT_PAGES] of them are requested at once, kept in listing order. Without one, each page starts after the items
 * received and paging stops at the first empty page, so a server that returns fewer items than asked for isn't mistaken
 * for the end of the listing. A page identical to the one before it ends the listing too, since a server that ignores
 * the offset would otherwise repeat it forever.
 *
 * Each page is requested up to three times: a failure the server could clear (it couldn't be reached, it answered 5xx or
 * 429) waits and tries again, any other failure ends the listing at once.
 *
 * With a [key], an item already received is dropped (a server whose listing shifts mid-paging repeats items across
 * pages), and when the server gave a total and the result falls short of it, the listing is emitted as not
 * [FlowEvent.Success.complete], so an import doesn't delete against it: a server's total can count items it never
 * returns (Jellyfin's, with access filtering), so a short listing is still a listing, just not a complete one. A listing
 * that legitimately repeats an item (a playlist) passes no key and gets neither.
 */
fun <T, R> pagedFlow(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    key: ((T) -> Any)? = null,
    convert: (T) -> R,
    fetchPage: suspend (offset: Int, limit: Int) -> NetworkResult<Page<T>>
): Flow<FlowEvent<List<R>, MessageProgress>> = flow {
    val items = mutableListOf<R>()
    val seen = HashSet<Any>()

    fun add(page: List<T>) {
        page.forEach { item -> if (key == null || seen.add(key(item))) items += convert(item) }
    }

    val first = when (val result = retrying { fetchPage(0, pageSize) }) {
        is NetworkResult.Success -> result.body
        is NetworkResult.Failure -> return@flow fail(result.error)
    }
    val total = first.totalCount
    if (total == null) {
        var page = first.items
        var previous: List<T>? = null
        var offset = 0
        while (true) {
            if (page.isNotEmpty() && page == previous) {
                logger.error { "A page repeated the one before it; the server is ignoring the offset" }
                break
            }
            emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, null)))
            if (page.isEmpty()) break
            add(page)
            previous = page
            offset += page.size
            page = when (val result = retrying { fetchPage(offset, pageSize) }) {
                is NetworkResult.Success -> result.body.items
                is NetworkResult.Failure -> return@flow fail(result.error)
            }
        }
        emit(FlowEvent.Success(items))
        return@flow
    }

    var reportedTotal: Int = total
    emit(fetchingProgress(min(pageSize, total), total))
    add(first.items)
    val failure = coroutineScope {
        val offsets = (pageSize until total step pageSize).iterator()
        val inFlight = ArrayDeque<Pair<Int, Deferred<NetworkResult<Page<T>>>>>()

        fun requestNext() {
            if (!offsets.hasNext()) return
            val offset = offsets.next()
            inFlight.addLast(offset to async { retrying { fetchPage(offset, min(pageSize, total - offset)) } })
        }
        repeat(CONCURRENT_PAGES) { requestNext() }
        while (inFlight.isNotEmpty()) {
            val (offset, request) = inFlight.removeFirst()
            when (val result = request.await()) {
                is NetworkResult.Success -> {
                    requestNext()
                    result.body.totalCount?.let { reportedTotal = it }
                    emit(fetchingProgress(min(offset + pageSize, total), total))
                    add(result.body.items)
                }

                is NetworkResult.Failure -> {
                    inFlight.forEach { (_, pending) -> pending.cancel() }
                    return@coroutineScope result.error
                }
            }
        }
        null
    }
    if (failure != null) return@flow fail(failure)

    val short = key != null && items.size < reportedTotal
    if (short) {
        logger.warn { "The server reported $reportedTotal items but the listing came to ${items.size}; treating it as incomplete" }
    }
    emit(FlowEvent.Success(items, complete = !short))
}

/** [pagedFlow], keeping the server's items as they are. */
fun <T> pagedFlow(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    key: ((T) -> Any)? = null,
    fetchPage: suspend (offset: Int, limit: Int) -> NetworkResult<Page<T>>
): Flow<FlowEvent<List<T>, MessageProgress>> = pagedFlow(pageSize, key, convert = { it }, fetchPage)

private fun fetchingProgress(
    current: Int,
    total: Int
): FlowEvent<Nothing, MessageProgress> = FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, Progress(current, total)))

private suspend fun FlowCollector<FlowEvent<Nothing, MessageProgress>>.fail(error: Throwable) {
    logger.error(error) { error.userDescription() }
    emit(FlowEvent.Failure(error.userDescription()))
}
