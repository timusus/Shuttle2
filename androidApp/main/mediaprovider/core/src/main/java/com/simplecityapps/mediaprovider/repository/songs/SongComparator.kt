package com.simplecityapps.mediaprovider.repository.songs

import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.sorting.SongSortOrder
import kotlinx.datetime.LocalDate
import java.text.Collator

val SongSortOrder.comparator: Comparator<Song>
    get() {
        return when (this) {
            SongSortOrder.Default -> SongComparator.defaultComparator
            SongSortOrder.SongName -> SongComparator.songNameComparator
            SongSortOrder.ArtistGroupKey -> SongComparator.artistGroupKeyComparator
            SongSortOrder.AlbumGroupKey -> SongComparator.albumGroupKeyComparator
            SongSortOrder.Year -> SongComparator.yearComparator
            SongSortOrder.Duration -> SongComparator.durationComparator
            SongSortOrder.Track -> SongComparator.trackComparator
            SongSortOrder.PlayCount -> SongComparator.playCountComparator
            SongSortOrder.LastModified -> SongComparator.lastModifiedComparator
            SongSortOrder.LastCompleted -> SongComparator.lastCompletedComparator
        }
    }

object SongComparator {

    private val collator: Collator by lazy {
        Collator.getInstance().apply { strength = Collator.TERTIARY }
    }

    val defaultComparator: Comparator<Song> by lazy {
        Comparator<Song> { a, b -> collator.compare(a.albumGroupKey.key ?: "zzz", b.albumGroupKey.key ?: "zzz") }
            .then { a, b -> collator.compare(a.albumGroupKey.albumArtistGroupKey?.key ?: "zzz", b.albumGroupKey.albumArtistGroupKey?.key ?: "zzz") }
            .then(compareBy { song -> song.disc })
            .then(compareBy { song -> song.track })
    }

    val songNameComparator: Comparator<Song> by lazy {
        Comparator<Song> { a, b -> collator.compare(a.name, b.name) }
            .then(defaultComparator)
    }

    val artistGroupKeyComparator: Comparator<Song> by lazy {
        Comparator<Song> { a, b -> collator.compare(a.albumArtistGroupKey.key ?: "zzz", b.albumArtistGroupKey.key ?: "zzz") }
            .then(defaultComparator)
    }

    val albumGroupKeyComparator: Comparator<Song> by lazy {
        Comparator<Song> { a, b -> collator.compare(a.albumGroupKey.key ?: "zzz", b.albumGroupKey.key ?: "zzz") }
            .then(defaultComparator)
    }

    val yearComparator: Comparator<Song> by lazy {
        compareByDescending<Song, LocalDate?>(nullsFirst()) { song -> song.date }
            .then(defaultComparator)
    }

    val durationComparator: Comparator<Song> by lazy {
        compareBy<Song> { song -> song.duration }
            .then(defaultComparator)
    }

    val trackComparator: Comparator<Song> by lazy {
        compareBy<Song>(
            { song -> song.disc },
            { song -> song.track }
        )
            .then(defaultComparator)
    }

    val playCountComparator: Comparator<Song> by lazy {
        compareByDescending<Song> { song -> song.playCount }
            .then(defaultComparator)
    }

    val lastModifiedComparator: Comparator<Song> by lazy {
        compareByDescending<Song> { song -> song.dateModified?.epochSeconds ?: 0 / 60 } // Round to the nearest minute
            .then(defaultComparator)
    }

    val lastCompletedComparator: Comparator<Song> by lazy {
        compareByDescending<Song> { song -> song.lastCompleted }
            .then(defaultComparator)
    }
}