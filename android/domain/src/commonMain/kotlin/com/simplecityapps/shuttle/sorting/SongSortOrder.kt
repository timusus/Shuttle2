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

    /** Most recently added first: the Recently Added smart playlist's order. */
    DateAdded,
    LastCompleted,

    /** Most recently made a favourite first: the Favourites smart playlist's order. */
    Favourited
}
