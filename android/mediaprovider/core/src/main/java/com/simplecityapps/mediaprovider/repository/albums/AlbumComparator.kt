package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import java.text.Collator
import kotlin.random.Random

val AlbumSortOrder.comparator: Comparator<Album>
    get() {
        return when (this) {
            AlbumSortOrder.Default -> AlbumComparator.defaultComparator
            AlbumSortOrder.AlbumName -> AlbumComparator.albumNameComparator
            AlbumSortOrder.ArtistGroupKey -> AlbumComparator.artistGroupKeyComparator
            AlbumSortOrder.PlayCount -> AlbumComparator.playCountComparator
            AlbumSortOrder.Year -> AlbumComparator.yearComparator
            AlbumSortOrder.RecentlyPlayed -> AlbumComparator.recentlyPlayedComparator
            // Random needs a per-session seed, so it can't be exposed as a stateless comparator here.
            // Callers sort with AlbumComparator.random(seed) instead.
            AlbumSortOrder.Random -> error("Random sort order requires a seed; use AlbumComparator.random(seed)")
        }
    }

object AlbumComparator {
    private val collator by lazy {
        Collator.getInstance().apply { strength = Collator.TERTIARY }
    }

    val defaultComparator: Comparator<Album> by lazy {
        Comparator<Album> { a, b -> collator.compare(a.groupKey?.key ?: "zzz", b.groupKey?.key ?: "zzz") }
            .then { a, b -> collator.compare(a.groupKey?.albumArtistGroupKey?.key ?: "zzz", b.groupKey?.albumArtistGroupKey?.key ?: "zzz") }
    }

    val albumNameComparator: Comparator<Album> by lazy {
        Comparator<Album> { a, b -> collator.compare(a.groupKey?.key ?: "zzz", b.groupKey?.key ?: "zzz") }
            .then(defaultComparator)
    }

    val artistGroupKeyComparator: Comparator<Album> by lazy {
        Comparator<Album> { a, b -> collator.compare(a.groupKey?.albumArtistGroupKey?.key ?: "zzz", b.groupKey?.albumArtistGroupKey?.key ?: "zzz") }
            .then(defaultComparator)
    }

    val yearComparator: Comparator<Album> by lazy {
        compareByDescending<Album, Int?>(nullsFirst(), { album -> album.year })
            .then(defaultComparator)
    }

    val playCountComparator: Comparator<Album> by lazy {
        compareByDescending<Album> { album -> album.playCount }
            .then(defaultComparator)
    }

    val recentlyPlayedComparator: Comparator<Album> by lazy {
        compareByDescending<Album> { album -> album.lastSongCompleted }
            .then(defaultComparator)
    }

    // Keyed off groupKey rather than list index, so re-emissions of the same albums
    // (in whatever order the upstream flow produces) sort identically for a given seed.
    fun random(seed: Long): Comparator<Album> = compareBy { album -> Random(album.groupKey.hashCode().toLong() xor seed).nextLong() }
}
