package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import kotlinx.coroutines.flow.Flow
import java.io.Serializable
import java.text.Collator

interface AlbumArtistRepository {
    fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>>
}

sealed class AlbumArtistQuery(
    val predicate: ((AlbumArtist) -> Boolean),
    val sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default
) {
    class All(sortOrder: AlbumArtistSortOrder = AlbumArtistSortOrder.Default) :
        AlbumArtistQuery(
            predicate = { true },
            sortOrder = sortOrder
        )

    class ArtistGroupKey(val key: com.simplecityapps.mediaprovider.model.ArtistGroupKey?) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.groupKey == key }
        )

    class Search(private val query: String) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.name?.contains(query, ignoreCase = true) ?: false }
        )

    class PlayCount(private val count: Int) :
        AlbumArtistQuery(
            predicate = { albumArtist -> albumArtist.playCount >= count },
            sortOrder = AlbumArtistSortOrder.PlayCount
        )
}

enum class AlbumArtistSortOrder : Serializable {
    Default, PlayCount;

    val comparator: Comparator<AlbumArtist>
        get() {
            return when (this) {
                Default -> defaultComparator
                PlayCount -> playCountComparator
            }
        }

    companion object {
        private val collator by lazy {
            Collator.getInstance().apply { strength = Collator.TERTIARY }
        }

        val defaultComparator: Comparator<AlbumArtist> by lazy {
            Comparator { a, b -> collator.compare(a.groupKey.key ?: "", b.groupKey.key ?: "") }
        }

        val playCountComparator: Comparator<AlbumArtist> by lazy {
            defaultComparator.then(compareByDescending { albumArtist -> albumArtist.playCount })
        }
    }
}