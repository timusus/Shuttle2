package com.simplecityapps.shuttle.downloads

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The remote song stored under a download's path, or null when its provider is unknown or the song has left the
 * library.
 *
 * A lookup loads the provider's whole song list and scans it, so it runs on [ioDispatcher] rather than the caller's
 * main thread. Download failures arrive in bursts (one per queued download when a session expires), so the list
 * loaded for one failure is reused for lookups within [CACHE_TTL] of it and reloaded only once the burst has gone
 * quiet; [now] is a property so tests can move time.
 */
@SingleIn(AppScope::class)
class SongLookup
@Inject
constructor(
    private val songRepository: SongRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    internal var now: () -> Long = System::currentTimeMillis

    private var cache: CachedSongs? = null

    private class CachedSongs(val provider: MediaProviderType, val songs: List<Song>, val loadedAt: Long)

    suspend fun songAt(path: String): Song? {
        val type = MediaProviderType.entries.firstOrNull { type -> type.pathScheme?.let { path.startsWith("$it://") } == true } ?: return null
        val nowMs = now()
        val cached = cache
        val songs = if (cached != null && cached.provider == type && nowMs - cached.loadedAt < CACHE_TTL) {
            cached.songs
        } else {
            withContext(ioDispatcher) { songRepository.loadProviderSongs(type) }
                .also { loaded -> cache = CachedSongs(type, loaded, nowMs) }
        }
        return withContext(ioDispatcher) { songs.firstOrNull { it.path == path } }
    }

    private companion object {
        val CACHE_TTL = 5.seconds.inWholeMilliseconds
    }
}
