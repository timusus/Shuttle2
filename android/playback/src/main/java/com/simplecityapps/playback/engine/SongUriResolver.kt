package com.simplecityapps.playback.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Resolves a remote song's own URI (`jellyfin://`, `emby://`, `plex://`) to the URL it streams from, only when the
 * player opens it, so building a queue never waits on a server.
 *
 * [queued] records which song each URI belongs to, and [retainOnly] forgets the songs the playlist no longer holds.
 * [dataSourceFactory]'s data sources then ask [mediaResolver] for that song's stream when they open its URI. They open
 * it on one of the player's loader threads, which Media3 lets a [ResolvingDataSource.Resolver] block, so that thread
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
    private val resolutions = ConcurrentHashMap<String, Deferred<Uri>>()

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

    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory = ResolvingDataSource.Factory(upstream) { dataSpec -> resolve(dataSpec) }

    private fun resolve(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.isDirect()) return dataSpec
        val key = dataSpec.uri.toString()
        val resolution = resolutions[key] ?: startResolving(key, dataSpec.uri)
        val uri =
            try {
                resolution.awaitBlocking()
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: Exception) {
                // Forget the failure, so the next load asks again.
                resolutions.remove(key, resolution)
                throw e as? MediaResolutionException ?: MediaResolutionException("Resolving a ${dataSpec.uri.scheme} URI was cancelled", e)
            }
        return dataSpec.withUri(uri)
    }

    private fun startResolving(
        key: String,
        uri: Uri
    ): Deferred<Uri> {
        val song = songs[key] ?: throw MediaResolutionException("No song queued for ${uri.scheme} URI")
        val resolution =
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    Uri.parse(mediaResolver.resolve(song).uri)
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

    companion object {
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

/** [DefaultLoadErrorHandlingPolicy], except a load that failed to resolve its stream fails at once, with no retry. */
class S2LoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = if (loadErrorInfo.exception.isResolutionFailure()) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
}

/** Whether this, or anything that caused it, is a [MediaResolutionException]. */
fun Throwable.isResolutionFailure(): Boolean = generateSequence(this) { it.cause }.any { it is MediaResolutionException }
