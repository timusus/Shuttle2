package com.simplecityapps.shuttle.scrobbling.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.networking.createHttpClient
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidScrobblingModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideScrobbleDatabase(
        @ApplicationContext context: Context
    ): ScrobbleDatabase = Room.databaseBuilder(context, ScrobbleDatabase::class.java, ScrobbleDatabase.DATABASE_NAME).build()

    @SingleIn(AppScope::class)
    @Provides
    fun provideLastFmApi(okHttpClient: OkHttpClient): LastFmApi = LastFmApi(createHttpClient(preconfiguredClient = okHttpClient))
}
