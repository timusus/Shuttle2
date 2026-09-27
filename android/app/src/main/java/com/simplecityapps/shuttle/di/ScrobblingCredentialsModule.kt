package com.simplecityapps.shuttle.di

import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** `:android:scrobbling` never sees `BuildConfig` (`:android:app` is the only module allowed to), so the key and secret cross the boundary here. */
@InstallIn(SingletonComponent::class)
@Module
class ScrobblingCredentialsModule {
    @Provides
    @Singleton
    fun provideLastFmCredentials(): LastFmCredentials = LastFmCredentials(
        apiKey = BuildConfig.LASTFM_API_KEY,
        sharedSecret = BuildConfig.LASTFM_SHARED_SECRET
    )
}
