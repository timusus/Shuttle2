package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.mediaprovider.search.SearchHit
import com.simplecityapps.mediaprovider.search.SearchQuery
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Genre
import com.simplecityapps.shuttle.model.Playlist
import com.simplecityapps.shuttle.model.Song
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** The result types Search offers as filter chips. */
enum class SearchCategory { Artists, Albums, Songs, Genres, Playlists }

/**
 * Search results grouped by type, each group best first. [top] names the group whose first hit is the best match of
 * all, which the screen lifts out as the top result.
 */
data class SearchResults(
    val artists: List<SearchHit<AlbumArtist>> = emptyList(),
    val albums: List<SearchHit<Album>> = emptyList(),
    val songs: List<SearchHit<Song>> = emptyList(),
    val genres: List<SearchHit<Genre>> = emptyList(),
    val playlists: List<SearchHit<Playlist>> = emptyList(),
    val top: SearchCategory? = null,
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty() && genres.isEmpty() && playlists.isEmpty()
}

/** Searches the [LibrarySearchIndex] for the enabled [SearchCategory]s. Re-emits as the library changes. */
class SearchLibrary @Inject constructor(
    private val libraryIndex: LibrarySearchIndex,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    operator fun invoke(query: String, categories: Set<SearchCategory>): Flow<SearchResults> {
        val searchQuery = SearchQuery.parse(query)
        if (searchQuery.isEmpty || categories.isEmpty()) return flowOf(SearchResults())
        return libraryIndex.index
            .map { index -> index.search(searchQuery) { it.category in categories }.grouped() }
            .flowOn(dispatcher)
    }

    private fun List<SearchHit<Any>>.grouped(): SearchResults {
        val artists = mutableListOf<SearchHit<AlbumArtist>>()
        val albums = mutableListOf<SearchHit<Album>>()
        val songs = mutableListOf<SearchHit<Song>>()
        val genres = mutableListOf<SearchHit<Genre>>()
        val playlists = mutableListOf<SearchHit<Playlist>>()
        for (hit in this) {
            when (val item = hit.item) {
                is AlbumArtist -> artists += hit.map { item }
                is Album -> albums += hit.map { item }
                is Song -> songs += hit.map { item }
                is Genre -> genres += hit.map { item }
                is Playlist -> playlists += hit.map { item }
            }
        }
        return SearchResults(artists, albums, songs, genres, playlists, top = firstOrNull()?.item?.category)
    }
}

private val Any.category: SearchCategory?
    get() = when (this) {
        is AlbumArtist -> SearchCategory.Artists
        is Album -> SearchCategory.Albums
        is Song -> SearchCategory.Songs
        is Genre -> SearchCategory.Genres
        is Playlist -> SearchCategory.Playlists
        else -> null
    }
