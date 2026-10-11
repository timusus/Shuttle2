package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.Song

/**
 * Asks a remote provider's server for songs like a seed (Jellyfin and Emby's Instant Mix), so "Start radio" can use the
 * server's own idea of similar. A provider with nothing to offer simply has no implementation; radio then falls back to
 * the library alone.
 *
 * An album or artist seed is passed as one of its songs: the servers take a song id, and its neighbours are the same.
 */
interface SimilarSongsProvider {
    fun handles(song: Song): Boolean

    /**
     * The provider's own ids ([Song.externalId]) of up to [limit] songs similar to [seed], most relevant first. Empty when
     * the server has none or can't be reached; the ids may include songs not in the library, which callers drop.
     */
    suspend fun similarSongIds(
        seed: Song,
        limit: Int
    ): List<String>
}

/** Routes each call to the first of [providers] that handles the seed; a seed none handle has no similar songs. */
class AggregateSimilarSongsProvider(private val providers: Set<SimilarSongsProvider>) : SimilarSongsProvider {
    override fun handles(song: Song): Boolean = providers.any { it.handles(song) }

    override suspend fun similarSongIds(
        seed: Song,
        limit: Int
    ): List<String> = providers.firstOrNull { it.handles(seed) }?.similarSongIds(seed, limit).orEmpty()
}
