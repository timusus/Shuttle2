package com.simplecityapps.shuttle.ui.screens.search

/**
 * One result group as a search screen lays it out: the hits of [category] from [from] until [until] (exclusive) of
 * its [total]. [from] is 1 when the group's first hit is lifted out as the top result.
 */
data class SearchSection(
    val category: SearchCategory,
    val from: Int,
    val until: Int,
    val total: Int,
) {
    /** More hits than shown, so the header offers "See all". */
    val hasMore: Boolean get() = until < total
}

/** How many hits each section shows before "See all", unless it's the only section with results or it's expanded. */
val SearchSectionLimits: Map<SearchCategory, Int> = mapOf(
    SearchCategory.Artists to 3,
    SearchCategory.Albums to 3,
    SearchCategory.Songs to 5,
    SearchCategory.Genres to 3,
    SearchCategory.Playlists to 3,
)

/** The number of hits of [category] in these results. */
fun SearchResults.count(category: SearchCategory): Int = when (category) {
    SearchCategory.Artists -> artists.size
    SearchCategory.Albums -> albums.size
    SearchCategory.Songs -> songs.size
    SearchCategory.Genres -> genres.size
    SearchCategory.Playlists -> playlists.size
}

/**
 * The sections to show, in [SearchCategory] order, each capped at its [SearchSectionLimits] entry unless it's the
 * [expanded] one (the user chose "See all") or the only group with results. The [SearchResults.top] group leaves out
 * its first hit, which the screen shows as the top result; a group left with nothing is dropped.
 */
fun SearchResults.sections(expanded: SearchCategory? = null): List<SearchSection> {
    val onlySection = SearchCategory.entries.count { count(it) > 0 } == 1
    return SearchCategory.entries.mapNotNull { category ->
        val total = count(category)
        val from = if (top == category) 1 else 0
        if (total <= from) return@mapNotNull null
        val limit = if (onlySection || category == expanded) total else SearchSectionLimits.getValue(category)
        SearchSection(category, from, until = minOf(from + limit, total), total)
    }
}
