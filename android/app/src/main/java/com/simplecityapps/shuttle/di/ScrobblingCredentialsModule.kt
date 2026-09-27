package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** `:android:scrobbling` never sees `BuildConfig` (`:android:app` is the only module allowed to), so the key and secret cross the boundary here. */
@ContributesTo(AppScope::class)
@BindingContainer
class ScrobblingCredentialsModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideLastFmCredentials(): LastFmCredentials = LastFmCredentials(
        apiKey = BuildConfig.LASTFM_API_KEY,
        sharedSecret = BuildConfig.LASTFM_SHARED_SECRET
    )
}
