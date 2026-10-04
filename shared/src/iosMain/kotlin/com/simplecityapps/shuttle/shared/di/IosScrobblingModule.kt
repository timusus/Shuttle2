package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.networking.NetworkConnectivity
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.scrobbling.flush.InProcessScrobbleFlushScheduler
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
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

/**
 * Last.fm scrobbling on iOS (#503): what `:android:scrobbling`'s common code needs that only the platform can build.
 * The queue's database sits next to the library's (in memory for an isolated graph, like it), the client is Darwin's,
 * and flushes run in-process ([InProcessScrobbleFlushScheduler]): Swift also asks for one on foreground and from a
 * `BGAppRefreshTask`. The API key and secret come from Info.plist (`S2LastFmApiKey`, `S2LastFmSharedSecret`, set from
 * `ios/Config/LastFm.xcconfig`); a build without them hides the Scrobbling row. The session sits in the Keychain
 * through `SecurePreferenceManager`, as on Android.
 */
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
        fun provideLastFmCredentials(): LastFmCredentials = LastFmCredentials(
            apiKey = infoString("S2LastFmApiKey"),
            sharedSecret = infoString("S2LastFmSharedSecret")
        )

        private fun infoString(key: String): String = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.trim().orEmpty()
    }
}
