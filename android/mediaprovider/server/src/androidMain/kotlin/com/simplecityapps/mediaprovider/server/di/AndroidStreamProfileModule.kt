package com.simplecityapps.mediaprovider.server.di

import com.simplecityapps.mediaprovider.server.StreamProfile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** Media3's [StreamProfile], for the Jellyfin and Emby stream URLs. */
@ContributesTo(AppScope::class)
@BindingContainer
object AndroidStreamProfileModule {
    @Provides
    fun provideStreamProfile(): StreamProfile = StreamProfile.Android
}
