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

/**
 * Whether one query for `Audio` items over all the user's libraries finds just the songs of those [collectionTypes] that
 * [isMusicLibrary] reads, so its total, which the server counts once per item however many libraries hold it, is the
 * song count. That's so when every library is read for songs or holds no audio; a library of audiobooks (or one that
 * gathers items from others, like `folders`) would add items the sync doesn't read (#934).
 */
fun countsAcrossLibraries(collectionTypes: List<String?>): Boolean = collectionTypes.all { type -> isMusicLibrary(type) || type?.trim()?.lowercase() in NO_AUDIO_COLLECTION_TYPES }
