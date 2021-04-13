package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.model.removeArticles
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

    class ArtistGroupKey(val key: com.simplecityapps.mediaprovider.model.ArtistGroupKey) :
        AlbumQuery(
            predicate = { album -> album.groupKey.artistGroupKey == key }
        )

    class AlbumGroupKey(private val albumGroupKey: com.simplecityapps.mediaprovider.model.AlbumGroupKey) :
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
    Default, AlbumName, ArtistName, Year, PlayCount;

    val comparator: Comparator<Album>
        get() {
            return when (this) {
                Default -> defaultComparator
                AlbumName -> albumNameComparator
                ArtistName -> artistNameComparator
                PlayCount -> playCountComparator
                Year -> yearComparator
            }
        }

    companion object {
        private val collator by lazy { Collator.getInstance().apply { strength = Collator.TERTIARY } }
        val defaultComparator: Comparator<Album> by lazy { Comparator<Album> { a, b -> zeroLastComparator.compare(a.year, b.year) }.then(compareByDescending { album -> album.year }) }
        val albumNameComparator: Comparator<Album> by lazy { Comparator<Album> { a, b -> collator.compare(a.sortKey, b.sortKey) }.then(defaultComparator) }
        val artistNameComparator: Comparator<Album> by lazy { Comparator<Album> { a, b -> collator.compare(a.albumArtist?.removeArticles(), b.albumArtist?.removeArticles()) }.then(defaultComparator) }
        val playCountComparator: Comparator<Album> by lazy { compareByDescending<Album> { album -> album.playCount }.then(defaultComparator) }
        val yearComparator: Comparator<Album> by lazy { defaultComparator }
    }
}