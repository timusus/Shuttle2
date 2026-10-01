package com.simplecityapps.shuttle.sorting

enum class AlbumSortOrder {
    Default,
    AlbumName,
    ArtistGroupKey,
    Year,
    PlayCount,
    RecentlyPlayed,

    /** Most recently added first: by the newest song added to each album. */
    DateAdded,
    Random
}
