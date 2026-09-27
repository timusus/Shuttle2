package com.simplecityapps.shuttle.scrobbling.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.SecurePreferenceLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
abstract class ScrobblingBindingsModule {
    @Binds
    abstract fun bindLastFmSessionStore(impl: SecurePreferenceLastFmSessionStore): LastFmSessionStore
}

@BindingContainer
@ContributesTo(AppScope::class)
object ScrobblingModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideScrobbleDatabase(
        @ApplicationContext context: Context
    ): ScrobbleDatabase = Room.databaseBuilder(context, ScrobbleDatabase::class.java, ScrobbleDatabase.DATABASE_NAME).build()

    @Provides
    fun provideScrobbleDao(database: ScrobbleDatabase): ScrobbleDao = database.scrobbleDao()

    @SingleIn(AppScope::class)
    @Provides
    fun provideLastFmApi(okHttpClient: OkHttpClient): LastFmApi {
        val httpClient: HttpClient = createHttpClient(preconfiguredClient = okHttpClient)
        return LastFmApi(httpClient)
    }
}
