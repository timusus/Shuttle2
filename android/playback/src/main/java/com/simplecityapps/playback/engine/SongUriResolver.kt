package com.simplecityapps.playback.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.simplecityapps.playback.exoplayer.MediaResolver
import com.simplecityapps.playback.queue.QueueEntry
import com.simplecityapps.playback.queue.toMediaItem
import com.simplecityapps.playback.queue.uri
import com.simplecityapps.shuttle.model.Song
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * Resolves a remote song's own URI (`jellyfin://`, `emby://`, `plex://`) to the URL it streams from, only when the
 * player opens it, so building a queue never waits on a server.
 *
 * [toMediaItem] records which song each URI belongs to. [dataSourceFactory]'s data sources then ask [mediaResolver]
 * for that song's stream when they open its URI, on the player's loading thread. File, content and http(s) URIs
 * open as they are.
 */
class SongUriResolver(
    private val mediaResolver: MediaResolver
) {
    private val songs = ConcurrentHashMap<String, Song>()

    /** Each URI's stream, once resolved, so a seek or retry doesn't ask the server again. */
    private val resolvedUris = ConcurrentHashMap<String, Uri>()

    /** The [MediaItem] the player queues for [entry], whose URI this resolver can open. */
    fun toMediaItem(entry: QueueEntry): MediaItem {
        val uri = entry.song.uri()
        if (!uri.isDirect()) {
            songs[uri.toString()] = entry.song
            // A newly queued item resolves afresh, as a stream URL can carry a token that expires.
            resolvedUris.remove(uri.toString())
        }
        return entry.toMediaItem()
    }

    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory = ResolvingDataSource.Factory(upstream) { dataSpec -> resolve(dataSpec) }

    private fun resolve(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.isDirect()) return dataSpec
        val key = dataSpec.uri.toString()
        resolvedUris[key]?.let { return dataSpec.withUri(it) }
        val song = songs[key] ?: throw MediaResolutionException("No song queued for ${dataSpec.uri.scheme} URI")
        val resolved =
            try {
                runBlocking { mediaResolver.resolve(song) }
            } catch (e: Exception) {
                throw MediaResolutionException("Failed to resolve ${song.name}", e)
            }
        val uri = Uri.parse(resolved.uri)
        resolvedUris[key] = uri
        return dataSpec.withUri(uri)
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
