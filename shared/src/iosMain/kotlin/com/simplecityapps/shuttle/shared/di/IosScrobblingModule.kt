package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.networking.NetworkConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.scrobbling.flush.InProcessScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.listenbrainz.ListenBrainzApi
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.queue.inMemoryScrobbleDatabaseBuilder
import com.simplecityapps.shuttle.scrobbling.queue.scrobbleDatabaseBuilder
import com.simplecityapps.shuttle.shared.IosStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import platform.Foundation.NSBundle

/** Flushes run in-process because Swift also triggers them on foreground and from a `BGAppRefreshTask`. */
@ContributesTo(AppScope::class)
@BindingContainer
abstract class IosScrobblingModule {
    @Binds
    abstract fun bindScrobbleFlushScheduler(scheduler: InProcessScrobbleFlushScheduler): ScrobbleFlushScheduler

    companion object {
        @Provides
        @SingleIn(AppScope::class)
        fun provideScrobbleDatabase(storage: IosStorage): ScrobbleDatabase {
            val builder = if (storage.isolatedName == null) scrobbleDatabaseBuilder() else inMemoryScrobbleDatabaseBuilder()
            return builder.build()
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideLastFmApi(connectivity: NetworkConnectivity): LastFmApi = LastFmApi(createHttpClient(connectivity = connectivity))

        @Provides
        @SingleIn(AppScope::class)
        fun provideListenBrainzApi(connectivity: NetworkConnectivity): ListenBrainzApi = ListenBrainzApi(createHttpClient(connectivity = connectivity))

        @Provides
        fun provideLastFmCredentials(): LastFmCredentials = LastFmCredentials(
            apiKey = infoString("S2LastFmApiKey"),
            sharedSecret = infoString("S2LastFmSharedSecret")
        )

        private fun infoString(key: String): String = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.trim().orEmpty()
    }
}
