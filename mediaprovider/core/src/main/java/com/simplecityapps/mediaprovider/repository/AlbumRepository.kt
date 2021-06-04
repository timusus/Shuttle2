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

    class ArtistGroupKey(val key: com.simplecityapps.mediaprovider.model.ArtistGroupKey?) :
        AlbumQuery(
            predicate = { album -> album.groupKey?.artistGroupKey == key }
        )

    class AlbumGroupKey(private val albumGroupKey: com.simplecityapps.mediaprovider.model.AlbumGroupKey?) :
        AlbumQuery(
            predicate = { album -> album.groupKey == albumGroupKey }
        )

    class AlbumGroupKeys(val albums: List<AlbumGroupKey>) :
        AlbumQuery(
            predicate = { album -> albums.any { it.predicate(album) } }
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
    Default, AlbumName, ArtistGroupKey, Year, PlayCount;

    val comparator: Comparator<Album>
        get() {
            return when (this) {
                Default -> defaultComparator
                AlbumName -> albumNameComparator
                ArtistGroupKey -> artistGroupKeyComparator
                PlayCount -> playCountComparator
                Year -> yearComparator
            }
        }

    companion object {
        private val collator by lazy {
            Collator.getInstance().apply { strength = Collator.TERTIARY }
        }

        private val defaultComparator: Comparator<Album> by lazy {
            compareByDescending<Album, Int?>(nullsFirst(), { album -> album.year })
                .then { a, b -> collator.compare(a.groupKey?.key ?: "", b.groupKey?.key ?: "") }
                .then { a, b -> collator.compare(a.groupKey?.artistGroupKey?.key ?: "", b.groupKey?.artistGroupKey?.key ?: "") }
        }

        val albumNameComparator: Comparator<Album> by lazy {
            Comparator<Album> { a, b -> collator.compare(a.groupKey?.key ?: "", b.groupKey?.key ?: "") }
                .then(defaultComparator)
        }

        val artistGroupKeyComparator: Comparator<Album> by lazy {
            Comparator<Album> { a, b -> collator.compare(a.groupKey?.artistGroupKey?.key ?: "", b.groupKey?.artistGroupKey?.key ?: "") }
                .then(defaultComparator)
        }

        val yearComparator: Comparator<Album> by lazy {
            defaultComparator
        }

        val playCountComparator: Comparator<Album> by lazy {
            compareByDescending<Album> { album -> album.playCount }
                .then(defaultComparator)
        }
    }
}