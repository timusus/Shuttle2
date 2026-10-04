package com.simplecityapps.shuttle.model

enum class MediaProviderType(val remote: Boolean, val supportsTagEditing: Boolean) {
    Shuttle(remote = false, supportsTagEditing = true),
    MediaStore(remote = false, supportsTagEditing = false),
    Emby(remote = true, supportsTagEditing = false),
    Jellyfin(remote = true, supportsTagEditing = false),
    Plex(remote = true, supportsTagEditing = false)
    ;

    /** The scheme a remote provider's `Song.path`s start with (`jellyfin://item/<id>`); null for the local ones. */
    val pathScheme: String?
        get() = when (this) {
            Emby -> "emby"
            Jellyfin -> "jellyfin"
            Plex -> "plex"
            Shuttle, MediaStore -> null
        }

    companion object {
        fun init(ordinal: Int): MediaProviderType = when (ordinal) {
            Shuttle.ordinal -> Shuttle
            MediaStore.ordinal -> MediaStore
            Emby.ordinal -> Emby
            Jellyfin.ordinal -> Jellyfin
            Plex.ordinal -> Plex
            else -> Shuttle
        }
    }
}
