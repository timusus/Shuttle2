package com.simplecityapps.imageloading.coil

import coil3.ImageLoader
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import com.simplecityapps.shuttle.model.AlbumArtist
import com.simplecityapps.shuttle.model.ArtistHeroArtwork

/**
 * An artist's image wherever only the artist is to hand (their rows in lists, search and home): the shared rule's
 * ([ArtistHeroArtwork]), resolved from their library and loaded as their page's hero is, so the row and the page show
 * the same image (#823).
 */
internal class AlbumArtistArtworkFetcher(
    private val artist: AlbumArtist,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val artistArtwork: suspend (AlbumArtist) -> ArtistHeroArtwork,
    private val heroFetcher: ArtworkFetcher.Factory<ArtistHeroArtwork>,
) : Fetcher {
    override suspend fun fetch(): FetchResult? = heroFetcher.create(artistArtwork(artist), options, imageLoader).fetch()

    class Factory(
        private val artistArtwork: suspend (AlbumArtist) -> ArtistHeroArtwork,
        private val heroFetcher: ArtworkFetcher.Factory<ArtistHeroArtwork>,
    ) : Fetcher.Factory<AlbumArtist> {
        override fun create(
            data: AlbumArtist,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = AlbumArtistArtworkFetcher(data, options, imageLoader, artistArtwork, heroFetcher)
    }
}
