package com.simplecityapps.mediaprovider.repository

import com.simplecityapps.mediaprovider.model.AlbumArtist
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

interface AlbumArtistRepository {
    fun getAlbumArtists(): Flow<List<AlbumArtist>>
    fun getAlbumArtists(query: AlbumArtistQuery): Flow<List<AlbumArtist>>
}

sealed class AlbumArtistQuery(
    val predicate: ((com.simplecityapps.mediaprovider.model.AlbumArtist) -> Boolean),
    val sortOrder: AlbumArtistSortOrder? = null
) {
    class AlbumArtist(val name: String) : AlbumArtistQuery({ albumArtist -> albumArtist.name.equals(name, ignoreCase = true) })
    class Search(private val query: String) : AlbumArtistQuery({ albumArtist -> albumArtist.name.contains(query, ignoreCase = true) })
    class PlayCount(private val count: Int, sortOrder: AlbumArtistSortOrder) : AlbumArtistQuery({ albumArtist -> albumArtist.playCount >= count }, sortOrder)
}

enum class AlbumArtistSortOrder : Serializable {
    PlayCount;

    val comparator: Comparator<AlbumArtist>
        get() {
            return when (this) {
                PlayCount -> Comparator { a, b -> a.playCount.compareTo(b.playCount) }
            }
        }
}