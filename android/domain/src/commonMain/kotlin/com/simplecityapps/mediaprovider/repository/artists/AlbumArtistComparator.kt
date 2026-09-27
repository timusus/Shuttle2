package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import com.simplecityapps.shuttle.sorting.CollationStrength
import com.simplecityapps.shuttle.sorting.localeCollator

val AlbumArtistSortOrder.comparator: Comparator<AlbumArtist>
    get() {
        return when (this) {
            AlbumArtistSortOrder.Default -> AlbumArtistComparator.defaultComparator
            AlbumArtistSortOrder.PlayCount -> AlbumArtistComparator.playCountComparator
        }
    }

object AlbumArtistComparator {
    private val collator by lazy { localeCollator(CollationStrength.Tertiary) }

    val defaultComparator: Comparator<AlbumArtist> by lazy {
        Comparator { a, b -> collator.compare(a.groupKey.key ?: "zzz", b.groupKey.key ?: "zzz") }
    }

    val playCountComparator: Comparator<AlbumArtist> by lazy {
        defaultComparator.then(compareByDescending { albumArtist -> albumArtist.playCount })
    }
}
