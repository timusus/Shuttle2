package com.simplecityapps.playback.di

import com.simplecityapps.mediaprovider.AggregateMediaInfoProvider
import com.simplecityapps.mediaprovider.MediaInfoProvider
import com.simplecityapps.mediaprovider.ServerStreamPolicy
import com.simplecityapps.playback.engine.SongUriResolver
import com.simplecityapps.playback.exoplayer.MediaInfoMediaResolver
import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
class MediaResolutionModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideAggregateMediaInfoProvider(
        // Each remote provider module contributes its own entry (see MediaProviderTypeKey)
        providers: Map<MediaProviderType, @JvmSuppressWildcards MediaInfoProvider>,
        serverStreamPolicy: ServerStreamPolicy
    ): AggregateMediaInfoProvider = AggregateMediaInfoProvider(providers.values, serverStreamPolicy)

    @SingleIn(AppScope::class)
    @Provides
    fun provideSongUriResolver(mediaInfoProvider: AggregateMediaInfoProvider): SongUriResolver = SongUriResolver(MediaInfoMediaResolver(mediaInfoProvider))
}
