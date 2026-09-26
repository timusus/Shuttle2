package com.simplecityapps.playback.chromecast

import com.simplecityapps.playback.awaitBlocking
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import timber.log.Timber

/**
 * Serves a Cast receiver each song at `/<key>/songs/<id>/audio` and its artwork at `/<key>/songs/<id>/artwork`, and
 * refuses (403) any request without the session's key (see [CastStreams]).
 *
 * A remote-provider song is a redirect to its server's stream rather than a proxy of it: a transcoded Jellyfin or Emby
 * stream is an HLS playlist whose segments the receiver resolves against the server's URL, which a proxy would have to
 * rewrite, and every byte would otherwise pass through the phone, and stop when it sleeps. The redirect's URL holds
 * the provider's credential, so only a holder of the key, which rides in the queue sent to the receiver, can read it.
 *
 * NanoHTTPD answers each request on a thread of its own, and wants the response returned there. A remote song's
 * stream, resolved before the song was sent (see [CastStreams]), is answered at once; anything else is looked up (see
 * [lookUp]) in [scope] while the request's thread waits, and stopping the server cancels what's still being looked up.
 */
class HttpServer(
    private val castService: CastService,
    private val streams: CastStreams,
    port: Int = CastMediaItemConverter.PORT,
    ioContext: CoroutineContext = Dispatchers.IO
) : NanoHTTPD(port) {
    private val scope = CoroutineScope(SupervisorJob() + ioContext)

    override fun stop() {
        super.stop()
        scope.coroutineContext.cancelChildren()
    }

    override fun serve(session: IHTTPSession): Response {
        val paths = session.uri.trim('/').split('/')
        if (!streams.isValid(paths.firstOrNull())) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", "Forbidden")
        }
        val songId = paths.takeIf { it.size == 4 && it[1] == "songs" }?.get(2)?.toLongOrNull()
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", "Invalid request url")

        return when (paths[3]) {
            "audio" -> streams.resolvedUrl(songId)?.let(::redirect) ?: lookUp { audio(session.headers, songId) }
            "artwork" -> lookUp { artwork(songId) }
            else -> newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", "Invalid request url")
        }
    }

    private suspend fun audio(
        headers: MutableMap<String, String>,
        songId: Long
    ): Response {
        castService.getRemoteAudioUrl(songId)?.let { url -> return redirect(url) }
        return castService.getAudio(songId)?.let { audioStream ->
            serveAudio(headers, audioStream.stream, audioStream.length, audioStream.mimeType)
        } ?: notFound()
    }

    private suspend fun artwork(songId: Long): Response = castService.getArtwork(songId)?.let { byteArray ->
        serveArtwork(ByteArrayInputStream(byteArray), "image/jpeg", byteArray.size.toLong())
    } ?: notFound()

    /**
     * Runs [lookup] in [scope] and waits for its response on the request's thread (see [awaitBlocking]). One that's
     * still running after [LOOKUP_TIMEOUT], or that the server stopped, answers 503, so no request thread waits on it.
     */
    private fun lookUp(lookup: suspend () -> Response): Response {
        val response = scope.async { lookup() }
        return try {
            response.awaitBlocking(LOOKUP_TIMEOUT)
        } catch (e: InterruptedIOException) {
            response.cancel()
            unavailable(e)
        } catch (e: TimeoutException) {
            response.cancel()
            unavailable(e)
        } catch (e: CancellationException) {
            unavailable(e)
        } catch (e: Exception) {
            Timber.e(e, "Failed to serve a Cast request")
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/html", "Internal error")
        }
    }

    private fun unavailable(e: Exception): Response {
        Timber.w(e, "Gave up on a Cast request")
        return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/html", "Unavailable")
    }

    private fun notFound(): Response = newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "File not found")

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

    companion object {
        private val LOOKUP_TIMEOUT = 30.seconds
    }
}
