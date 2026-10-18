package com.simplecityapps.mediaprovider.server

/**
 * The Jellyfin and Emby library kinds (`CollectionType`, as /Users/{id}/Views lists them) that hold no music, though some
 * hold `Audio` items: audiobooks (Jellyfin's `books`, Emby's `audiobooks` and `books`), and the views that gather items
 * from the other libraries (`boxsets`, `playlists`, and `folders`, the folder view of every library, audiobooks included).
 */
private val NON_MUSIC_COLLECTION_TYPES = setOf(
    "audiobooks",
    "books",
    "boxsets",
    "folders",
    "games",
    "homevideos",
    "livetv",
    "movies",
    "musicvideos",
    "photos",
    "playlists",
    "trailers",
    "tvshows"
)

/** Excluded by kind, not included: a song missing from the listing is deleted, so only a kind known to hold no music is skipped. */
fun isMusicLibrary(collectionType: String?): Boolean = collectionType?.trim()?.lowercase() !in NON_MUSIC_COLLECTION_TYPES

/** The library kinds of [NON_MUSIC_COLLECTION_TYPES] that hold no `Audio` items at all, so a query over every library never meets one of their items. */
private val NO_AUDIO_COLLECTION_TYPES = setOf(
    "games",
    "homevideos",
    "livetv",
    "movies",
    "musicvideos",
    "photos",
    "trailers",
    "tvshows"
)

/** One `Audio` query across all libraries totals the songs only when every library is read for songs or holds no audio. */
fun countsAcrossLibraries(collectionTypes: List<String?>): Boolean = collectionTypes.all { type -> isMusicLibrary(type) || type?.trim()?.lowercase() in NO_AUDIO_COLLECTION_TYPES }
