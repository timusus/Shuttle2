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
 * so a stream reopened as the original (a network or settings change) clears the transcode it replaced.
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
        formats.update { if (format == null) it - path else it + (path to format) }
    }
}
