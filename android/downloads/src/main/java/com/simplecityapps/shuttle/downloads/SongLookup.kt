package com.simplecityapps.shuttle.downloads

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song

/** The remote song stored under [path], or null when its provider is unknown or the song has left the library. */
internal suspend fun SongRepository.songAt(path: String): Song? {
    val type = MediaProviderType.entries.firstOrNull { type -> type.pathScheme?.let { path.startsWith("$it://") } == true } ?: return null
    return loadProviderSongs(type).firstOrNull { it.path == path }
}
