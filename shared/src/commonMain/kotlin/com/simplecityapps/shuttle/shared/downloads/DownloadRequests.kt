package com.simplecityapps.shuttle.shared.downloads

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.persistence.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * The songs whose downloads have been asked for and not yet finished or removed, kept across launches: a download that
 * fails while the app isn't running is reported at the next launch, when its song has to be known to list it and retry it.
 * Each is the song's id and name by `Song.path`; the song itself is loaded again from the library when it's retried.
 */
class DownloadRequests(private val store: KeyValueStore) {
    data class Request(
        val songId: Long,
        val title: String
    )

    private val requests = MutableStateFlow(read())

    operator fun get(path: String): Request? = requests.value[path]

    fun put(song: Song) = write { it + (song.path to Request(song.id, song.name ?: "")) }

    fun remove(path: String) = write { it - path }

    private fun write(transform: (Map<String, Request>) -> Map<String, Request>) {
        val next = requests.updateAndGet(transform)
        store.edit { putString(KEY, next.entries.joinToString("\n") { (path, request) -> "${request.songId}\t${path.clean()}\t${request.title.clean()}" }) }
    }

    private fun read(): Map<String, Request> = store.getString(KEY, null).orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 3)
        val id = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val path = parts.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        path to Request(id, parts.getOrNull(2).orEmpty())
    }.toMap()

    private fun String.clean() = replace('\t', ' ').replace('\n', ' ')

    private companion object {
        const val KEY = "offline_download_requests"
    }
}
