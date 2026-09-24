package com.simplecityapps.playback.chromecast

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Serves a Cast receiver each song at `/<key>/songs/<id>/audio` and its artwork at `/<key>/songs/<id>/artwork`, and
 * refuses (403) any request without the session's key (see [CastStreams]).
 *
 * A remote-provider song is a redirect to its server's stream rather than a proxy of it: a transcoded Jellyfin or Emby
 * stream is an HLS playlist whose segments the receiver resolves against the server's URL, which a proxy would have to
 * rewrite, and every byte would otherwise pass through the phone, and stop when it sleeps. The redirect's URL holds
 * the provider's credential, so only a holder of the key, which rides in the queue sent to the receiver, can read it.
 */
class HttpServer(
    private val castService: CastService,
    private val streams: CastStreams,
    port: Int = CastMediaItemConverter.PORT
) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response {
        val paths = session.uri.trim('/').split('/')
        if (!streams.isValid(paths.firstOrNull())) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "Forbidden")
        }
        val songId = paths.takeIf { it.size == 4 && it[1] == "songs" }?.get(2)?.toLongOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", "Invalid request url")

        return when (paths[3]) {
            "audio" -> runBlocking {
                castService.getRemoteAudioUrl(songId)?.let { url ->
                    return@runBlocking redirect(url)
                }
                castService.getAudio(songId)?.let { audioStream ->
                    serveAudio(session.headers, audioStream.stream, audioStream.length, audioStream.mimeType)
                } ?: newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found")
            }

            "artwork" -> runBlocking {
                castService.getArtwork(songId)?.let { byteArray ->
                    serveArtwork(ByteArrayInputStream(byteArray), "image/jpeg", byteArray.size.toLong())
                } ?: newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found")
            }

            else -> newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", "Invalid request url")
        }
    }

    private fun serveAudio(
        headers: MutableMap<String, String>,
        inputStream: InputStream,
        length: Long,
        mimeType: String
    ): Response {
        try {
            var range: String? = null
            for (key in headers.keys) {
                if ("range" == key) {
                    range = headers[key]
                }
            }

            if (range == null) {
                range = "bytes=0-"
                headers["range"] = range
            }

            val start: Long
            var end: Long

            val rangeValue = range.trim { character -> character <= ' ' }.substring("bytes=".length)

            if (rangeValue.startsWith("-")) {
                end = length - 1
                start = length - 1 - java.lang.Long.parseLong(rangeValue.substring("-".length))
            } else {
                val ranges = rangeValue.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                start = java.lang.Long.parseLong(ranges[0])
                end = if (ranges.size > 1) java.lang.Long.parseLong(ranges[1]) else length - 1
            }
            if (end > length - 1) {
                end = length - 1
            }

            if (start <= end) {
                val contentLength = end - start + 1
                inputStream.skip(start)
                val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength)
                response.addHeader("Content-Length", contentLength.toString() + "")
                response.addHeader("Content-Range", "bytes $start-$end/$length")
                response.addHeader("Content-Type", mimeType)
                return response
            } else {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/html", range)
            }
        } catch (e: IOException) {
            Timber.e(e, "Error serving audio")
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found")
    }

    private fun redirect(url: String): Response = newFixedLengthResponse(Response.Status.TEMPORARY_REDIRECT, "text/html", "").apply {
        addHeader("Location", url)
    }

    private fun serveArtwork(
        inputStream: InputStream,
        mimeType: String,
        length: Long
    ): Response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, length)
}
