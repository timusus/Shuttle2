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

/**
 * Whether a Jellyfin or Emby library of [collectionType] is read for songs (#845). Libraries are excluded by kind, not
 * included: a song missing from the listing is deleted, with its play history and playlist places, so only a kind known
 * to hold no music is left out. A library of mixed content (no type, or "mixed") or of a kind this doesn't know is read.
 */
fun isMusicLibrary(collectionType: String?): Boolean = collectionType?.trim()?.lowercase() !in NON_MUSIC_COLLECTION_TYPES
