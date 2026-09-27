package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import com.simplecityapps.shuttle.sorting.CollationStrength
import com.simplecityapps.shuttle.sorting.localeCollator
import com.simplecityapps.shuttle.sorting.splitMix64

// Takes a seed so Random can't be forgotten: every caller must supply one, even though only
// Random uses it. Callers that reshuffle on reselect (rather than every re-emission) generate
// the seed once at selection time and pass the same value back in on each call.
fun AlbumSortOrder.comparator(seed: Long): Comparator<Album> = when (this) {
    AlbumSortOrder.Default -> AlbumComparator.defaultComparator
    AlbumSortOrder.AlbumName -> AlbumComparator.albumNameComparator
    AlbumSortOrder.ArtistGroupKey -> AlbumComparator.artistGroupKeyComparator
    AlbumSortOrder.PlayCount -> AlbumComparator.playCountComparator
    AlbumSortOrder.Year -> AlbumComparator.yearComparator
    AlbumSortOrder.RecentlyPlayed -> AlbumComparator.recentlyPlayedComparator
    AlbumSortOrder.Random -> AlbumComparator.random(seed)
}

object AlbumComparator {
    private val collator by lazy { localeCollator(CollationStrength.Tertiary) }

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
    // A stateless hash rather than a seeded Random per album: cheap per comparison, and safe to
    // share between concurrent flow collectors without a memo to synchronize.
    fun random(seed: Long): Comparator<Album> = compareBy { album -> splitMix64(album.groupKey.hashCode().toLong() xor seed) }
}
