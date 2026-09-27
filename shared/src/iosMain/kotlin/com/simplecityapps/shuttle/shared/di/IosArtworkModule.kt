package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import com.simplecityapps.provider.emby.EmbyRemoteArtworkProvider
import com.simplecityapps.provider.jellyfin.JellyfinRemoteArtworkProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Artwork urls on the signed-in Jellyfin or Emby server, the iOS twin of Android's `ImageLoaderModule` aggregate. */
@ContributesTo(AppScope::class)
@BindingContainer
class IosArtworkModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideRemoteArtworkProvider(
        jellyfin: JellyfinRemoteArtworkProvider,
        emby: EmbyRemoteArtworkProvider
    ): RemoteArtworkProvider = AggregateRemoteArtworkProvider(setOf(jellyfin, emby))
}
