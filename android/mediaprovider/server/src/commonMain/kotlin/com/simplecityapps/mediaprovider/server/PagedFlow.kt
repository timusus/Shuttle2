package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.Progress
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.networking.userDescription
import com.simplecityapps.shuttle.logging.Logger
import kotlin.math.min
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
 */
fun <T> pagedFlow(
    pageSize: Int = DEFAULT_PAGE_SIZE,
    fetchPage: suspend (offset: Int, limit: Int) -> NetworkResult<Page<T>>
): Flow<FlowEvent<List<T>, MessageProgress>> = flow {
    val items = mutableListOf<T>()
    var offset = 0
    var limit = pageSize
    var previous: List<T>? = null
    while (true) {
        when (val result = fetchPage(offset, limit)) {
            is NetworkResult.Success -> {
                val page = result.body
                val totalCount = page.totalCount
                if (totalCount == null && page.items.isNotEmpty() && page.items == previous) {
                    logger.error { "A page repeated the one before it; the server is ignoring the offset" }
                    emit(FlowEvent.Success(items))
                    return@flow
                }
                previous = page.items
                val end = if (totalCount != null) offset + limit else offset + page.items.size
                emit(FlowEvent.Progress(MessageProgress(ImportPhase.Fetching, totalCount?.let { total -> Progress(min(end, total), total) })))
                items.addAll(page.items)

                val hasMore = if (totalCount != null) end < totalCount else page.items.isNotEmpty()
                if (!hasMore) {
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
