package com.simplecityapps.shuttle.ui.screens.library

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumGroupKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Which album sections of a grouped song list are unfolded. The first load opens every album when there are
 * only a few, else none; later loads keep the user's choice, and keys of albums no longer listed are dropped.
 */
class AlbumExpansion {
    /** Null until the first load applies the default, so a later rescan never reapplies it. */
    private val expanded = MutableStateFlow<Set<AlbumGroupKey>?>(null)

    val flow: Flow<Set<AlbumGroupKey>> = expanded.map { it.orEmpty() }

    fun onLoaded(albums: List<Album>) {
        expanded.compareAndSet(null, if (albums.size <= MAX_EXPANDED_BY_DEFAULT) albums.keys() else emptySet())
    }

    fun toggle(album: Album, present: List<Album>) {
        val key = album.groupKey ?: return
        expanded.update { current ->
            val kept = current.orEmpty().intersect(present.keys())
            if (key in kept) kept - key else kept + key
        }
    }

    fun expandAll(present: List<Album>) {
        expanded.value = present.keys()
    }

    fun collapseAll() {
        expanded.value = emptySet()
    }

    private companion object {
        /** At most this many albums start expanded; more start collapsed so the list stays scannable. */
        const val MAX_EXPANDED_BY_DEFAULT = 2

        fun List<Album>.keys(): Set<AlbumGroupKey> = mapNotNullTo(HashSet()) { it.groupKey }
    }
}
