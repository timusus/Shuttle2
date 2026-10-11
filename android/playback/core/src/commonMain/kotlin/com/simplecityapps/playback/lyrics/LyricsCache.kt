package com.simplecityapps.playback.lyrics

import com.simplecityapps.shuttle.persistence.KeyValueStore

/**
 * Online lookups kept per song so a song isn't fetched on every view, "not found" included. An entry expires:
 * lyrics rarely change, but a song LRCLIB lacks may gain them. Bounded to [MAX_ENTRIES], oldest dropped first.
 */
class LyricsCache(
    private val store: KeyValueStore,
    private val now: () -> Long,
) {
    sealed interface Entry {
        data class Found(val text: String) : Entry

        data object NotFound : Entry
    }

    /** The unexpired entry for [songId], or null when it must be fetched. */
    fun get(songId: Long): Entry? {
        val raw = store.getString(key(songId), null) ?: return null
        val (fetchedAt, text) = raw.substringBefore('\n').toLongOrNull()?.let { it to raw.substringAfter('\n') } ?: return null
        val age = now() - fetchedAt
        return if (text.isEmpty()) {
            Entry.NotFound.takeIf { age in 0 until NOT_FOUND_TTL_MS }
        } else {
            Entry.Found(text).takeIf { age in 0 until FOUND_TTL_MS }
        }
    }

    fun put(songId: Long, text: String?) {
        val ids = (store.getString(INDEX_KEY, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() } - songId) + songId
        val evicted = ids.take((ids.size - MAX_ENTRIES).coerceAtLeast(0))
        store.edit {
            evicted.forEach { remove(key(it)) }
            putString(key(songId), "${now()}\n${text.orEmpty()}")
            putString(INDEX_KEY, ids.drop(evicted.size).joinToString(","))
        }
    }

    private fun key(songId: Long) = "lyrics_cache_$songId"

    companion object {
        const val MAX_ENTRIES = 300
        const val FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val INDEX_KEY = "lyrics_cache_index"
    }
}
