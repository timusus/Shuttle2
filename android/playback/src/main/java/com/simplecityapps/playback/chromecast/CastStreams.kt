package com.simplecityapps.playback.chromecast

import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.playback.queue.QueueEntry
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
 * What a Cast receiver streams each queue entry from, and the secret every URL the phone's [HttpServer] serves carries.
 *
 * The server listens on the whole network with no other check, and a remote-provider song's stream is a redirect to a
 * URL holding the provider's credential, so a request without this session's [key] gets nothing. A new key comes with
 * each Cast session.
 *
 * A remote-provider entry's stream is resolved before it's sent (see [resolve]), as the one the receiver can play: a
 * Jellyfin or Emby server transcodes what the receiver can't, to HLS, and only asking it says so. The receiver needs
 * the real content type to play it, and a converter must answer at once, so it reads what was resolved. Streams are
 * kept per entry, opened under its [QueueEntry.playId], so the server's stream and the playback reports name one play.
 */
class CastStreams(
    private val mediaInfoProvider: MediaInfoProvider,
    private val ioContext: CoroutineContext = Dispatchers.IO
) {
    /** An entry's stream: its content type, and for a remote-provider song, where its server streams it from. */
    private class Stream(val contentType: String, val url: String?)

    private val random = SecureRandom()

    /** By entry uid. */
    private val streams = ConcurrentHashMap<Long, Stream>()

    @Volatile
    var key: String = newKey()
        private set

    /** Replaces the key as a Cast session starts, so no URL from an earlier session is served, and forgets the streams. */
    fun newSession() {
        key = newKey()
        streams.clear()
    }

    /** Forgets the streams of all entries but [uids], as the receiver is sent a new window. */
    fun retainOnly(uids: Collection<Long>) {
        streams.keys.retainAll(uids.toHashSet())
    }

    /** Whether [candidate] is this session's key, compared in constant time. */
    fun isValid(candidate: String?): Boolean = candidate != null && MessageDigest.isEqual(candidate.toByteArray(), key.toByteArray())

    /** Whether [entry] can be sent as it is: a local song, or a remote-provider one whose stream was resolved. */
    fun isResolved(entry: QueueEntry): Boolean = !entry.song.mediaProvider.remote || streams.containsKey(entry.uid)

    /** The content type the receiver plays [entry] as. */
    fun contentType(entry: QueueEntry): String = streams[entry.uid]?.contentType ?: entry.song.mimeType

    /**
     * Resolves the streams of those of [entries] that aren't yet, a few at a time and roughly in order. One that fails
     * to resolve is sent as its own type, and asked for again as the receiver fetches it.
     */
    suspend fun resolve(entries: List<QueueEntry>) {
        val unresolved = entries.filterNot(::isResolved).distinctBy { it.uid }
        if (unresolved.isEmpty()) return
        val permits = Semaphore(CONCURRENCY)
        coroutineScope {
            unresolved.map { entry -> async { permits.withPermit { fetch(entry) } } }.awaitAll()
        }
    }

    /** Where the server streams the remote-provider entry [uid] from, if that's been resolved; else null. */
    fun resolvedUrl(uid: Long): String? = streams[uid]?.url

    /** Where the server streams a remote-provider [entry] from; null for a local song. */
    suspend fun remoteUrl(entry: QueueEntry): String? {
        if (!entry.song.mediaProvider.remote) return null
        return streams[entry.uid]?.url ?: fetch(entry)?.url
    }

    private suspend fun fetch(entry: QueueEntry): Stream? {
        val song = entry.song
        val stream = try {
            withContext(ioContext) { mediaInfoProvider.getMediaInfo(song, castCompatibilityMode = true, playId = entry.playId) }
                .let { info -> Stream(info.mimeType, info.path.toString().takeIf { info.isRemote }) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve the Cast stream of song ${song.id}")
            null
        }
        streams[entry.uid] = stream ?: Stream(song.mimeType, null)
        return stream
    }

    private fun newKey(): String = ByteArray(KEY_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_BYTES = 16

        /** How many streams are resolved at once. */
        private const val CONCURRENCY = 6
    }
}
