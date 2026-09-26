package com.simplecityapps.imageloading.coil

import coil3.key.Keyer
import coil3.request.Options
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.Song

/** Keys a song's artwork in both caches; the prefix keeps it apart from an album or artist whose names happen to match. */
internal fun Song.artworkCacheKey(): String = "song:${albumArtist ?: friendlyArtistName}_${album}_$name".withArtworkVersion(artworkVersion)

internal fun Album.artworkCacheKey(): String = "album:${albumArtist ?: friendlyArtistName}_$name".withArtworkVersion(artworkVersion)

internal fun AlbumArtist.artworkCacheKey(): String = "artist:${name ?: friendlyArtistName ?: "Unknown"}".withArtworkVersion(artworkVersion)

/**
 * Appends the provider's artwork version, so the key changes exactly when the artwork does. Without a version
 * (not yet reimported, or a provider with no signal) the key keeps its unversioned form and existing cache entries stay valid.
 */
private fun String.withArtworkVersion(artworkVersion: String?): String = if (artworkVersion == null) this else "${this}_$artworkVersion"

internal object SongArtworkKeyer : Keyer<Song> {
    override fun key(
        data: Song,
        options: Options
    ): String = data.artworkCacheKey()
}

internal object AlbumArtworkKeyer : Keyer<Album> {
    override fun key(
        data: Album,
        options: Options
    ): String = data.artworkCacheKey()
}

internal object AlbumArtistArtworkKeyer : Keyer<AlbumArtist> {
    override fun key(
        data: AlbumArtist,
        options: Options
    ): String = data.artworkCacheKey()
}
