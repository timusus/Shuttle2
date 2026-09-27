package com.simplecityapps.shuttle.scrobbling.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.shuttle.scrobbling.lastfm.LASTFM_BASE_URL
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.ScrobblesJsonAdapterFactory
import com.simplecityapps.shuttle.scrobbling.lastfm.SecurePreferenceLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LastFmMoshi

@Module
@InstallIn(SingletonComponent::class)
abstract class ScrobblingBindingsModule {
    @Binds
    abstract fun bindLastFmSessionStore(impl: SecurePreferenceLastFmSessionStore): LastFmSessionStore
}

@Module
@InstallIn(SingletonComponent::class)
object ScrobblingModule {
    @Singleton
    @Provides
    fun provideScrobbleDatabase(
        @ApplicationContext context: Context
    ): ScrobbleDatabase = Room.databaseBuilder(context, ScrobbleDatabase::class.java, ScrobbleDatabase.DATABASE_NAME).build()

    @Provides
    fun provideScrobbleDao(database: ScrobbleDatabase): ScrobbleDao = database.scrobbleDao()

    @LastFmMoshi
    @Singleton
    @Provides
    fun provideLastFmMoshi(): Moshi = Moshi.Builder()
        .add(ScrobblesJsonAdapterFactory())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Singleton
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
