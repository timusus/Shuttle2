package com.simplecityapps.shuttle.ui.actions

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.model.copyAlbumKey
import com.simplecityapps.shuttle.model.copyKey
import dev.zacsweers.metro.Inject

/** A song's copies, itself included, whether or not the library merged them. */
internal val Song.allCopies: List<Song> get() = copies.ifEmpty { listOf(this) }

/**
 * Makes [copy] the one that stands for [song], replacing any other copy made primary. The siblings come from the merged
 * [song]: a copy is a leaf member and knows no others.
 */
@Inject
class SetPrimaryCopy(private val store: CopyPreferencesStore) {
    operator fun invoke(song: Song, copy: Song) {
        val others = song.allCopies.map { it.copyKey } - copy.copyKey
        store.update { it.copy(primaryCopies = it.primaryCopies - others.toSet() + copy.copyKey) }
    }
}

/** Back to the automatic choice for [song]: no copy of it stays primary. */
@Inject
class ClearPrimaryCopy(private val store: CopyPreferencesStore) {
    operator fun invoke(song: Song) {
        val keys = song.allCopies.map { it.copyKey }.toSet() + song.copyKey
        store.update { it.copy(primaryCopies = it.primaryCopies - keys) }
    }
}

/**
 * Makes [provider]'s copies of [albumSongs] the album's primary source; null is Automatic. Only one source of an album
 * is primary, so the album's other sources are cleared.
 */
@Inject
class SetAlbumPrimarySource(private val store: CopyPreferencesStore) {
    operator fun invoke(albumSongs: List<Song>, provider: MediaProviderType?) {
        val all = albumSongs.flatMap { it.allCopies + it }
        val chosen = all.filter { it.mediaProvider == provider }.map { it.copyAlbumKey }.toSet()
        val others = all.map { it.copyAlbumKey }.toSet() - chosen
        store.update { it.copy(primaryAlbums = it.primaryAlbums - others + chosen) }
    }
}
