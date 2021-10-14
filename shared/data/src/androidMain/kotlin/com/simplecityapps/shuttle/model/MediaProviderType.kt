package com.simplecityapps.shuttle.model

actual enum class MediaProviderType {
    Shuttle,
    MediaStore,
    Emby,
    Jellyfin,
    Plex;
}

actual fun MediaProviderType.isRemote(): Boolean {
    return when (this) {
        MediaProviderType.Shuttle -> false
        MediaProviderType.MediaStore -> false
        MediaProviderType.Emby -> true
        MediaProviderType.Jellyfin -> true
        MediaProviderType.Plex -> true
    }
}

actual fun MediaProviderType.supportsTagEditing(): Boolean {
    return when (this) {
        MediaProviderType.Shuttle -> true
        MediaProviderType.MediaStore -> false
        MediaProviderType.Emby -> false
        MediaProviderType.Jellyfin -> false
        MediaProviderType.Plex -> false
    }
}