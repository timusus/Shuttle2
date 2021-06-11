package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import kotlinx.coroutines.flow.Flow
import java.io.Serializable
import java.text.Collator

interface AlbumRepository {
    fun getAlbums(query: AlbumQuery): Flow<List<Album>>
}

sealed class AlbumQuery(
    val predicate: ((Album) -> Boolean),
    val sortOrder: AlbumSortOrder = AlbumSortOrder.Default
) {

    class All(sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    class ArtistGroupKey(val key: com.simplecityapps.mediaprovider.model.AlbumArtistGroupKey?) :
        AlbumQuery(
            predicate = { album -> album.groupKey?.albumArtistGroupKey == key }
        )

    class AlbumGroupKey(private val albumGroupKey: com.simplecityapps.mediaprovider.model.AlbumGroupKey?, sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { album -> album.groupKey == albumGroupKey },
            sortOrder = sortOrder
        )

    class AlbumGroupKeys(val albums: List<AlbumGroupKey>, sortOrder: AlbumSortOrder = AlbumSortOrder.Default) :
        AlbumQuery(
            predicate = { album -> albums.any { it.predicate(album) } },
            sortOrder = sortOrder
        )

    class Search(val query: String) :
        AlbumQuery(
            predicate = { album -> album.name?.contains(query, true) ?: false || album.albumArtist?.contains(query, true) ?: false }
        )

    class PlayCount(val count: Int, sortOrder: AlbumSortOrder) :
        AlbumQuery(
            predicate = { album -> album.playCount >= count },
            sortOrder = sortOrder
        )

    class Year(val year: Int) :
        AlbumQuery(
            predicate = { album -> album.year == year }
        )
}

enum class AlbumSortOrder : Serializable {
    Default, AlbumName, ArtistGroupKey, Year, PlayCount, RecentlyPlayed;

    val comparator: Comparator<Album>
        get() {
            return when (this) {
                Default -> defaultComparator
                AlbumName -> albumNameComparator
                ArtistGroupKey -> artistGroupKeyComparator
                PlayCount -> playCountComparator
                Year -> yearComparator
                RecentlyPlayed -> recentlyPlayedComparator
            }
        }

    companion object {
        private val collator by lazy {
            Collator.getInstance().apply { strength = Collator.TERTIARY }
        }

        private val defaultComparator: Comparator<Album> by lazy {
            compareByDescending<Album, Int?>(nullsFirst(), { album -> album.year })
                .then { a, b -> collator.compare(a.groupKey?.key ?: "zzz", b.groupKey?.key ?: "zzz") }
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
            defaultComparator
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
}