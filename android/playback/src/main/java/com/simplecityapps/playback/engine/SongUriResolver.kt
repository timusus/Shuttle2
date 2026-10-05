package com.simplecityapps.playback.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.simplecityapps.mediaprovider.TimeSeekableStream
import com.simplecityapps.playback.awaitBlocking
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.queueEntry
import com.simplecityapps.playback.queue.uri
import com.simplecityapps.shuttle.model.Song
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Resolves a remote song's own URI (`jellyfin://`, `emby://`, `plex://`, `subsonic://`) to the URL it streams from, only when the
 * player opens it, so building a queue never waits on a server.
 *
 * [queued] records which song each URI belongs to, and [retainOnly] forgets the songs the playlist no longer holds.
 * [dataSourceFactory]'s data sources then ask [mediaResolver] for that song's stream when they open its URI. They open
 * it on one of the player's loader threads, which Media3 lets a data source's open block, so that thread
 * waits (see [awaitBlocking]) while the resolution runs on [ioContext]; cancelling the load interrupts the wait. File,
 * content and http(s) URIs open as they are.
 */
class SongUriResolver(
    private val mediaResolver: MediaResolver,
    ioContext: CoroutineContext = Dispatchers.IO
) {
    private val songs = ConcurrentHashMap<String, Song>()

    private val scope = CoroutineScope(SupervisorJob() + ioContext)

    /**
     * Each URI's stream, resolving or resolved, so a seek or retry, or the stream-type probe and then the player
     * opening the same URI, asks the server once.
     */
    private val resolutions = ConcurrentHashMap<String, Deferred<Resolution>>()

    /** Records the songs [items] play, so the player can open them. Call it before they join the playlist. */
    fun queued(items: List<MediaItem>) {
        items.forEach { item ->
            val uri = item.localConfiguration?.uri ?: return@forEach
            if (!uri.isDirect()) {
                songs[uri.toString()] = item.queueEntry.song
                // A newly queued item resolves afresh, as a stream URL can carry a token that expires. A load already
                // waiting on the earlier resolution (the same song, queued again) still gets it.
                resolutions.remove(uri.toString())
            }
        }
    }

    /** Forgets every song but those [playlist] holds, so what's recorded stays bounded by the playlist. */
    fun retainOnly(playlist: List<QueueEntry>) {
        // A queue of local files records nothing, and this runs on every change to the queue.
        if (songs.isEmpty() && resolutions.isEmpty()) return
        val keep = playlist.mapTo(HashSet()) { entry -> entry.song.uri().toString() }
        songs.keys.retainAll(keep)
        (resolutions.keys - keep).forEach { key -> resolutions.remove(key)?.cancel() }
    }

    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory = DataSource.Factory { SongDataSource(upstream.createDataSource()) }

    /**
     * Whether [uri] (a song's own URI) resolved to a stream that seeks by time. False until it has resolved, which it has
     * by the time the player picks the stream's extractor: that follows opening it.
     */
    fun isTimeSeekable(uri: Uri): Boolean = resolutions[uri.toString()]?.resolved()?.timeSeek != null

    /**
     * Whether [uri] (a song's own URI) resolved to a transcode the server drops when another starts (see
     * [com.simplecityapps.playback.exoplayer.ResolvedMedia.isReplaceableTranscode]). Its cached resolution stays valid after that: the URL starts the transcode
     * afresh each time it's requested, so the player opening it again restarts it.
     */
    fun isReplaceableTranscode(uri: Uri): Boolean = resolutions[uri.toString()]?.resolved()?.isReplaceableTranscode == true

    /**
     * Whether [url] (one the player fetched: a stream, or an HLS playlist or segment) is on the server of a transcode that
     * it drops when another starts, so a 404 from it means that transcode was replaced: retrying won't bring it back.
     */
    fun servesReplaceableTranscode(url: Uri): Boolean = resolutions.values.any { deferred ->
        val resolution = deferred.resolved() ?: return@any false
        resolution.isReplaceableTranscode && resolution.uri.scheme == url.scheme && resolution.uri.encodedAuthority == url.encodedAuthority
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Deferred<Resolution>.resolved(): Resolution? = takeIf { it.isCompleted && it.getCompletionExceptionOrNull() == null }?.getCompleted()

    private fun resolve(dataSpec: DataSpec): Resolution {
        val key = dataSpec.uri.toString()
        val resolution = resolutions[key] ?: startResolving(key, dataSpec.uri)
        return try {
            resolution.awaitBlocking()
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: Exception) {
            // Forget the failure, so the next load asks again.
            resolutions.remove(key, resolution)
            throw e as? MediaResolutionException ?: MediaResolutionException("Resolving a ${dataSpec.uri.scheme} URI was cancelled", e)
        }
    }

    private fun startResolving(
        key: String,
        uri: Uri
    ): Deferred<Resolution> {
        val song = songs[key] ?: throw MediaResolutionException("No song queued for ${uri.scheme} URI")
        val resolution =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    val media = mediaResolver.resolve(song)
                    Resolution(Uri.parse(media.uri), media.timeSeek, media.isReplaceableTranscode)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw MediaResolutionException("Failed to resolve ${song.name}", e)
                }
            }
        // Another loader may have started resolving this URI meanwhile; share its resolution.
        resolutions.putIfAbsent(key, resolution)?.let { existing ->
            resolution.cancel()
            return existing
        }
        resolution.start()
        return resolution
    }

    private class Resolution(
        val uri: Uri,
        val timeSeek: TimeSeekableStream?,
        val isReplaceableTranscode: Boolean
    )

    /**
     * Opens a remote song's URI as its stream, and direct URIs as they are. A stream that seeks by time
     * ([TimeSeekableStream]) is opened from the second the requested byte position stands for, then read on to the
     * position itself, and reported as long as its bitrate makes it, so the extractor (a constant-bitrate seeker) knows
     * its duration and maps a seek to a byte position this turns back into a time.
     */
    private inner class SongDataSource(private val upstream: DataSource) : DataSource {
        private var uri: Uri? = null

        override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            if (dataSpec.uri.isDirect()) return upstream.open(dataSpec).also { uri = dataSpec.uri }
            val resolution = resolve(dataSpec)
            val timeSeek = resolution.timeSeek ?: return upstream.open(dataSpec.withUri(resolution.uri)).also { uri = resolution.uri }
            val position = dataSpec.position
            val offsetSeconds = position / timeSeek.bytesPerSecond
            val streamUri = if (offsetSeconds > 0) Uri.parse(timeSeek.urlAt(offsetSeconds)) else resolution.uri
            uri = streamUri
            upstream.open(dataSpec.buildUpon().setUri(streamUri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
            skip(position - offsetSeconds * timeSeek.bytesPerSecond)
            return when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length

                // With no duration there's no estimate, so the length is unknown rather than nothing
                timeSeek.estimatedLength <= 0 -> C.LENGTH_UNSET.toLong()

                else -> (timeSeek.estimatedLength - position).coerceAtLeast(0)
            }
        }

        private fun skip(bytes: Long) {
            val buffer = ByteArray(SKIP_BUFFER_SIZE)
            var remaining = bytes
            while (remaining > 0) {
                val read = upstream.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (read == C.RESULT_END_OF_INPUT) return
                remaining -= read
            }
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int = upstream.read(buffer, offset, length)

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

        override fun close() {
            uri = null
            upstream.close()
        }
    }

    companion object {
        private const val SKIP_BUFFER_SIZE = 8 * 1024

        private val DIRECT_SCHEMES = setOf(null, "file", "content", "android.resource", "asset", "rawresource", "data", "http", "https")

        /** Whether the player can open this URI as it is, without asking a media provider for its stream. */
        fun Uri.isDirect(): Boolean = scheme?.lowercase() in DIRECT_SCHEMES
    }
}

/** A song's stream couldn't be resolved (e.g. its server can't be reached). Retrying the load won't help. */
class MediaResolutionException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * [DefaultLoadErrorHandlingPolicy], except these loads fail at once, with no retry: one that failed to resolve its stream,
 * and a 404 from the server of a transcode it drops when another starts ([isReplaceableTranscodeServer]: see
 * [SongUriResolver.servesReplaceableTranscode]), which [com.simplecityapps.playback.ItemLoader] re-opens instead.
 */
class S2LoadErrorHandlingPolicy(private val isReplaceableTranscodeServer: (Uri) -> Boolean = { false }) : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        val replacedTranscode = exception.notFound()?.let { notFound -> isReplaceableTranscodeServer(notFound.dataSpec.uri) } == true
        return if (exception.isResolutionFailure() || replacedTranscode) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }
}

/** Whether this, or anything that caused it, is a [MediaResolutionException]. */
fun Throwable.isResolutionFailure(): Boolean = generateSequence(this) { it.cause }.any { it is MediaResolutionException }

/** The 404 that this is, or that caused it, if any. */
fun Throwable.notFound(): HttpDataSource.InvalidResponseCodeException? = generateSequence(this) { it.cause }
    .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
    .firstOrNull { it.responseCode == HTTP_NOT_FOUND }

private const val HTTP_NOT_FOUND = 404
