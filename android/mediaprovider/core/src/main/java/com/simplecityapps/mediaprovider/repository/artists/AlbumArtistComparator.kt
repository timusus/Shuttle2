package com.simplecityapps.mediaprovider.repository.artists

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.sorting.AlbumArtistSortOrder
import java.text.Collator

val AlbumArtistSortOrder.comparator: Comparator<AlbumArtist>
    get() {
        return when (this) {
            AlbumArtistSortOrder.Default -> AlbumArtistComparator.defaultComparator
            AlbumArtistSortOrder.PlayCount -> AlbumArtistComparator.playCountComparator
        }
    }

object AlbumArtistComparator {
    private val collator by lazy {
        Collator.getInstance().apply { strength = Collator.TERTIARY }
    }

    val defaultComparator: Comparator<AlbumArtist> by lazy {
        Comparator { a, b -> collator.compare(a.groupKey.key ?: "zzz", b.groupKey.key ?: "zzz") }
    }

    val playCountComparator: Comparator<AlbumArtist> by lazy {
        defaultComparator.then(compareByDescending { albumArtist -> albumArtist.playCount })
    }
}
