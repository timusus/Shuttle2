package com.simplecityapps.mediaprovider.repository.albums

import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.sorting.AlbumSortOrder
import java.text.Collator

val AlbumSortOrder.comparator: Comparator<Album>
    get() {
        return when (this) {
            AlbumSortOrder.Default -> AlbumComparator.defaultComparator
            AlbumSortOrder.AlbumName -> AlbumComparator.albumNameComparator
            AlbumSortOrder.ArtistGroupKey -> AlbumComparator.artistGroupKeyComparator
            AlbumSortOrder.PlayCount -> AlbumComparator.playCountComparator
            AlbumSortOrder.Year -> AlbumComparator.yearComparator
            AlbumSortOrder.RecentlyPlayed -> AlbumComparator.recentlyPlayedComparator
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
}