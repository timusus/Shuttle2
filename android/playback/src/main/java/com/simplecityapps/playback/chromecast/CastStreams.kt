package com.simplecityapps.playback.chromecast

import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.shuttle.model.Song
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * What a Cast receiver streams each song from, and the secret every URL the phone's [HttpServer] serves carries.
 *
 * The server listens on the whole network with no other check, and a remote-provider song's stream is a redirect to a
 * URL holding the provider's credential, so a request without this session's [key] gets nothing. A new key comes with
 * each Cast session.
 *
 * A remote-provider song's stream is resolved before it's sent (see [resolve]), as the one the receiver can play: a
 * Jellyfin or Emby server transcodes what the receiver can't, to HLS, and only asking it says so. The receiver needs
 * the real content type to play it, and a converter must answer at once, so it reads what was resolved.
 */
class CastStreams(
    private val mediaInfoProvider: MediaInfoProvider,
    private val ioContext: CoroutineContext = Dispatchers.IO
) {
    /** A song's stream: its content type, and for a remote-provider song, where its server streams it from. */
    private class Stream(val contentType: String, val url: String?)

    private val random = SecureRandom()

    private val streams = ConcurrentHashMap<Long, Stream>()

    @Volatile
    var key: String = newKey()
        private set

    /** Replaces the key as a Cast session starts, so no URL from an earlier session is served, and forgets the streams. */
    fun newSession() {
        key = newKey()
        streams.clear()
    }

    /** Whether [candidate] is this session's key, compared in constant time. */
    fun isValid(candidate: String?): Boolean = candidate != null && MessageDigest.isEqual(candidate.toByteArray(), key.toByteArray())

    /** Whether [song] can be sent as it is: a local song, or a remote-provider one whose stream was resolved. */
    fun isResolved(song: Song): Boolean = !song.mediaProvider.remote || streams.containsKey(song.id)

    /** The content type the receiver plays [song] as. */
    fun contentType(song: Song): String = streams[song.id]?.contentType ?: song.mimeType

    /**
     * Resolves the streams of those of [songs] that aren't yet, a few at a time and roughly in order. One that fails
     * to resolve is sent as its own type, and asked for again as the receiver fetches it.
     */
    suspend fun resolve(songs: List<Song>) {
        val unresolved = songs.filterNot(::isResolved).distinctBy { it.id }
        if (unresolved.isEmpty()) return
        val permits = Semaphore(CONCURRENCY)
        coroutineScope {
            unresolved.map { song -> async { permits.withPermit { fetch(song) } } }.awaitAll()
        }
    }

    /** Where the server streams a remote-provider [song] from; null for a local song. */
    suspend fun remoteUrl(song: Song): String? {
        if (!song.mediaProvider.remote) return null
        return streams[song.id]?.url ?: fetch(song)?.url
    }

    private suspend fun fetch(song: Song): Stream? {
        val stream = try {
            withContext(ioContext) { mediaInfoProvider.getMediaInfo(song, castCompatibilityMode = true) }
                .let { info -> Stream(info.mimeType, info.path.toString().takeIf { info.isRemote }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve the Cast stream of song ${song.id}")
            null
        }
        streams[song.id] = stream ?: Stream(song.mimeType, null)
        return stream
    }

    private fun newKey(): String = ByteArray(KEY_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_BYTES = 16

        /** How many streams are resolved at once. */
        private const val CONCURRENCY = 6
    }
}
