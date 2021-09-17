package com.simplecityapps.shuttle.model

enum class MediaProviderType(val remote: Boolean, val supportsTagEditing: Boolean) {
    Shuttle(remote = false, supportsTagEditing = true),
    MediaStore(remote = false, supportsTagEditing = false),
    Emby(remote = true, supportsTagEditing = false),
    Jellyfin(remote = true, supportsTagEditing = false),
    Plex(remote = true, supportsTagEditing = false);

    companion object {
        fun init(ordinal: Int): MediaProviderType {
            return when (ordinal) {
                Shuttle.ordinal -> Shuttle
                MediaStore.ordinal -> MediaStore
                Emby.ordinal -> Emby
                Jellyfin.ordinal -> Jellyfin
                Plex.ordinal -> Plex
                else -> Shuttle
            }
        }
    }
}