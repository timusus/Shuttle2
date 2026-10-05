package com.simplecityapps.shuttle.streaming

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What a server sends for a song it transcodes, rather than the file it holds: the codec's display name (`MP3`, `AAC`,
 * `OPUS`) and the bitrate it was asked for, or null when the server picks it (a transcode with no cap).
 */
data class DeliveredFormat(
    val codec: String,
    val bitrateKbps: Int?
)

/**
 * The transcode each server song's latest stream was opened as, by song path: Now Playing's badge shows it in place of
 * the file's own format (#902), while Song Info keeps the file's. A song streamed as its original file has no entry,
 * so a stream reopened as the original (a network or settings change) clears the transcode it replaced, and so does a
 * song played from its download. Only the [MAX_ENTRIES] most recently recorded songs are kept.
 */
@SingleIn(AppScope::class)
class DeliveredFormats @Inject constructor() {
    private val formats = MutableStateFlow<Map<String, DeliveredFormat>>(emptyMap())

    val byPath: StateFlow<Map<String, DeliveredFormat>> = formats.asStateFlow()

    /** Records that [path]'s stream is [format], or the original file when it's null. */
    fun record(
        path: String,
        format: DeliveredFormat?
    ) {
        formats.update { current ->
            // Re-adding moves the path to the end of the (insertion-ordered) map, so the oldest are dropped first.
            if (format == null) current - path else (current - path + (path to format)).takeLast(MAX_ENTRIES)
        }
    }

    private fun <K, V> Map<K, V>.takeLast(n: Int): Map<K, V> = if (size <= n) this else entries.drop(size - n).associate { it.key to it.value }

    companion object {
        const val MAX_ENTRIES = 100
    }
}
