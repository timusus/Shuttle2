package com.simplecityapps.shuttle.sorting

enum class SongSortOrder {
    Default,
    SongName,
    ArtistGroupKey,
    AlbumGroupKey,
    Year,
    Duration,
    Track,
    PlayCount,
    LastModified,
    LastCompleted,

    /** Most recently made a favourite first: the Favourites smart playlist's order. */
    Favourited
}
