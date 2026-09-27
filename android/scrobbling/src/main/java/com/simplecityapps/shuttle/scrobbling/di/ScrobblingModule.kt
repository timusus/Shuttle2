package com.simplecityapps.shuttle.scrobbling.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.scrobbling.lastfm.LASTFM_BASE_URL
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.ScrobblesJsonAdapterFactory
import com.simplecityapps.shuttle.scrobbling.lastfm.SecurePreferenceLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFmMoshi

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

    @LastFmMoshi
    @SingleIn(AppScope::class)
    @Provides
    fun provideLastFmMoshi(): Moshi = Moshi.Builder()
        .add(ScrobblesJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @SingleIn(AppScope::class)
    @Provides
    fun provideLastFmApi(
        okHttpClient: OkHttpClient,
        @LastFmMoshi moshi: Moshi
    ): LastFmApi = Retrofit.Builder()
        .baseUrl(LASTFM_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(LastFmApi::class.java)
}
