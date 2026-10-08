package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import com.simplecityapps.provider.plex.PlexRemoteArtworkProvider
import com.simplecityapps.provider.subsonic.SignedSubsonicArtworkProvider
import com.simplecityapps.shuttle.model.ArtistHeroArtwork
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.ui.theme.ArtworkSeed
import com.simplecityapps.shuttle.ui.theme.ArtworkSeedSource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Artwork urls on the signed-in Jellyfin, Emby, Plex or Subsonic server, the iOS twin of Android's `ImageLoaderModule`
 * aggregate, and the artwork seed colour. Plex's urls carry no token: each request asks the provider for its
 * `X-Plex-Token` header. Subsonic's come signed, as it takes credentials only in the url.
 */
@ContributesTo(AppScope::class)
@BindingContainer
class IosArtworkModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRemoteArtworkProvider(
        jellyfin: JellyfinRemoteArtworkProvider,
        emby: EmbyRemoteArtworkProvider,
        plex: PlexRemoteArtworkProvider,
        subsonic: SignedSubsonicArtworkProvider
    ): RemoteArtworkProvider = AggregateRemoteArtworkProvider(setOf(jellyfin, emby, plex, subsonic))

    /**
     * No artwork seed on iOS yet: Swift decodes the artwork, and nothing extracts a
     * colour from it until the themed surfaces arrive with the player and detail screens (phase 7). [ArtworkSeed.None]
     * is what Android reports for artwork it can't seed from, so those surfaces keep the app's own scheme.
     */
    @Provides
    fun provideArtworkSeedSource(): ArtworkSeedSource = NoArtworkSeedSource
}

private object NoArtworkSeedSource : ArtworkSeedSource {
    override suspend fun seedFor(song: Song): ArtworkSeed = ArtworkSeed.None

    override suspend fun seedFor(hero: ArtistHeroArtwork): ArtworkSeed = ArtworkSeed.None
}
