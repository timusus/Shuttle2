package com.simplecityapps.shuttle.di

import com.simplecityapps.mediaprovider.AggregateRemoteArtworkProvider
import com.simplecityapps.mediaprovider.RemoteArtworkProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
abstract class ImageLoaderModule {
    // Each provider module contributes its own artwork provider to the set; declared here so it resolves, empty, with none installed.
    @Multibinds(allowEmpty = true)
    abstract fun remoteArtworkProviders(): Set<RemoteArtworkProvider>

    companion object {
        @SingleIn(AppScope::class)
        @Provides
        fun provideAggregateRemoteArtworkProvider(
            providers: Set<@JvmSuppressWildcards RemoteArtworkProvider>
        ): AggregateRemoteArtworkProvider = AggregateRemoteArtworkProvider(providers)
    }
}
