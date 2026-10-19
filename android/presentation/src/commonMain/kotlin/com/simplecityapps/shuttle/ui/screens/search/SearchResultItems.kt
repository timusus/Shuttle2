package com.simplecityapps.shuttle.ui.screens.search

import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.ui.actions.MediaSelection

/** One entry of the results list, top to bottom. */
sealed interface SearchResultItem {
    /** The best match of all, the first hit of [category], under a "Top result" header. */
    data class TopResult(val category: SearchCategory) : SearchResultItem

    data class Header(val category: SearchCategory, val seeAll: Boolean) : SearchResultItem

    /** The hit at [index] of its whole group, which a song plays from. */
    data class Hit(val category: SearchCategory, val index: Int) : SearchResultItem
}

/** The results list: the top result, then each of [sections]' header and rows, with [expanded] shown in full. */
fun SearchResults.resultItems(expanded: SearchCategory? = null): List<SearchResultItem> = buildList {
    top?.let { add(SearchResultItem.TopResult(it)) }
    sections(expanded).forEach { section ->
        add(SearchResultItem.Header(section.category, seeAll = section.hasMore))
        (section.from until section.until).forEach { add(SearchResultItem.Hit(section.category, it)) }
    }
}

/** What a result's actions and Play act on. */
fun SearchResults.selection(category: SearchCategory, index: Int): MediaSelection = when (category) {
    SearchCategory.Artists -> MediaSelection.AlbumArtists(artists[index].item)
    SearchCategory.Albums -> MediaSelection.Albums(albums[index].item)
    SearchCategory.Songs -> MediaSelection.Songs(songs[index].item)
    SearchCategory.Genres -> MediaSelection.Genres(genres[index].item)
    SearchCategory.Playlists -> MediaSelection.Playlists(playlists[index].item)
}

data class ResultCount(val kind: Kind, val count: Int) {
    enum class Kind { Albums, Songs }
}

/** An artist's summary: their albums, or for a track artist found by their credits, who has none, their songs. */
fun AlbumArtist.resultCount(): ResultCount = if (isAlbumArtist) ResultCount(ResultCount.Kind.Albums, albumCount) else ResultCount(ResultCount.Kind.Songs, songCount)
