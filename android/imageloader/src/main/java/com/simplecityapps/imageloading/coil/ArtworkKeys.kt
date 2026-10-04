package com.simplecityapps.imageloading.coil

import coil3.key.Keyer
import coil3.request.Options
import com.simplecityapps.shuttle.model.Album
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song

/**
 * Keys a song's artwork in both caches; the prefix keeps it apart from an album or artist whose names happen to match.
 * Public so the artwork seed can be cached under the same identity as the image it's extracted from.
 */
fun Song.artworkCacheKey(): String = "song:${albumArtist ?: friendlyArtistName}_${album}_$name".withArtworkVersion(artworkVersion)

internal fun Album.artworkCacheKey(): String = "album:${albumArtist ?: friendlyArtistName}_$name".withArtworkVersion(artworkVersion)

fun AlbumArtist.artworkCacheKey(): String = "artist:${name ?: friendlyArtistName ?: "Unknown"}".withArtworkVersion(artworkVersion)

/**
 * Keys an artist's image by the shared rule (#781, #823): the artist, whether it may use the online lookup, the album it falls
 * back to, and the smallest artist image it takes, so an image cached under an older minimum is looked up again.
 */
fun ArtistHeroArtwork.artworkCacheKey(): String = "artistHero:${artist.artworkCacheKey()}|online=$onlineLookup|${fallbackAlbum?.artworkCacheKey()}|min=${ArtistHeroArtwork.MIN_ARTIST_IMAGE_SIZE}"

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

internal object ArtistHeroArtworkKeyer : Keyer<ArtistHeroArtwork> {
    override fun key(
        data: ArtistHeroArtwork,
        options: Options
    ): String = data.artworkCacheKey()
}
