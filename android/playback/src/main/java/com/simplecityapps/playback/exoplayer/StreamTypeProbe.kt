package com.simplecityapps.playback.exoplayer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceUtil
import androidx.media3.datasource.DataSpec
import java.io.IOException

/** How a stream has to be played: as an HLS playlist, or as a single progressive file. */
enum class StreamType { Hls, Progressive }

/**
 * Works out whether an extensionless stream URL serves an HLS playlist or a progressive file.
 *
 * Jellyfin and Emby serve every remote song from `/Audio/{id}/universal`. It direct-streams
 * formats the device supports, but transcodes the rest (WMA, AIFF, ...) to HLS and answers with
 * `application/vnd.apple.mpegurl` and an `#EXTM3U` body. The songs are imported with the wildcard MIME type `Audio/<any>`, so
 * neither the URL nor the MIME type says which one Media3 will get.
 */
object StreamTypeProbe {
    /** Enough of the body to see an `#EXTM3U` header, even behind a UTF-8 byte order mark. */
    const val PROBE_LENGTH = 64

    private val HLS_MIME_TYPES = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "audio/mpegurl",
        "audio/x-mpegurl"
    )
    private val HLS_HEADER = "#EXTM3U".toByteArray(Charsets.US_ASCII)
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * Only a network URL with neither a definite MIME type nor a file extension needs probing.
     * Local files, content URIs, known MIME types and URLs with an extension (`.m3u8`, `.flac`, ...)
     * go straight to Media3's own inference, with no extra request.
     */
    fun needsProbe(
        uri: Uri,
        mimeType: String?
    ): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        if (mimeType != null && !mimeType.endsWith("/*")) return false
        if (Util.inferContentType(uri) != C.CONTENT_TYPE_OTHER) return false
        return uri.lastPathSegment?.contains('.') != true
    }

    /** HLS if the server labels the response as a playlist, or the body starts like one. */
    fun detect(
        contentType: String?,
        head: ByteArray
    ): StreamType {
        val mimeType = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (mimeType in HLS_MIME_TYPES) return StreamType.Hls
        val body = if (head.startsWith(UTF8_BOM)) head.copyOfRange(UTF8_BOM.size, head.size) else head
        return if (body.startsWith(HLS_HEADER)) StreamType.Hls else StreamType.Progressive
    }

    /**
     * Requests the first [PROBE_LENGTH] bytes of [uri] and [detect]s the stream type from the
     * response's Content-Type and body. Blocking; throws on a network or HTTP error.
     */
    @Throws(IOException::class)
    fun probe(
        dataSource: DataSource,
        uri: Uri
    ): StreamType {
        try {
            dataSource.open(DataSpec.Builder().setUri(uri).setLength(PROBE_LENGTH.toLong()).build())
            val head = ByteArray(PROBE_LENGTH)
            var length = 0
            while (length < head.size) {
                val read = dataSource.read(head, length, head.size - length)
                if (read == C.RESULT_END_OF_INPUT) break
                length += read
            }
            val contentType = dataSource.responseHeaders.entries
                .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
            return detect(contentType, head.copyOf(length))
        } finally {
            DataSourceUtil.closeQuietly(dataSource)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
