package com.simplecityapps.imageloading.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.ArtistImageArtwork
import com.simplecityapps.shuttle.model.Song

/**
 * An artist's image for a song shown as its artist (#952): the shared rule's ([ArtistHeroArtwork]) real artist images, their own
 * or the online lookup, but not its top-album fallback, then the song's own artwork when the artist has no image.
 */
internal class ArtistImageArtworkFetcher(
    private val model: ArtistImageArtwork,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val artistArtwork: suspend (AlbumArtist) -> ArtistHeroArtwork,
    private val heroFetcher: ArtworkFetcher.Factory<ArtistHeroArtwork>,
    private val songFetcher: ArtworkFetcher.Factory<Song>,
) : Fetcher {
    // Each part caches under its own key, not one a request gave this model
    private val partOptions = options.copy(diskCacheKey = null)

    override suspend fun fetch(): FetchResult? {
        val hero = artistArtwork(model.artist).copy(fallbackAlbum = null)
        return try {
            heroFetcher.create(hero, partOptions, imageLoader).fetch()
        } catch (_: ArtworkNotFoundException) {
            songFetcher.create(model.song, partOptions, imageLoader).fetch()
        }
    }

    class Factory(
        private val artistArtwork: suspend (AlbumArtist) -> ArtistHeroArtwork,
        private val heroFetcher: ArtworkFetcher.Factory<ArtistHeroArtwork>,
        private val songFetcher: ArtworkFetcher.Factory<Song>,
    ) : Fetcher.Factory<ArtistImageArtwork> {
        override fun create(
            data: ArtistImageArtwork,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = ArtistImageArtworkFetcher(data, options, imageLoader, artistArtwork, heroFetcher, songFetcher)
    }
}
