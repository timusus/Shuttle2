package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import java.text.Collator
import java.util.Collections
import kotlin.random.Random

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
    // Each key is computed once and memoized, rather than reconstructing a Random per comparison.
    fun random(seed: Long): Comparator<Album> {
        // Synchronized: the comparator may outlive one sort and be shared by concurrent flow collectors.
        val keysByGroupKey = Collections.synchronizedMap(HashMap<Any?, Long>())
        fun keyFor(album: Album): Long = keysByGroupKey.getOrPut(album.groupKey) { Random(album.groupKey.hashCode().toLong() xor seed).nextLong() }
        return compareBy(::keyFor)
    }
}
