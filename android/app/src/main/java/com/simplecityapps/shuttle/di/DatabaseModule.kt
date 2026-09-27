package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.localmediaprovider.local.data.room.DatabaseProvider
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.databaseBuilder
import com.simplecityapps.localmediaprovider.local.repository.PlaylistFileSync
import com.simplecityapps.localmediaprovider.local.repository.SafPlaylistFileSync
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
class DatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaDatabase(
        @ApplicationContext context: Context
    ): MediaDatabase = DatabaseProvider(databaseBuilder(context), isDebug = BuildConfig.DEBUG).database

    /** Keeps an m3u-imported playlist's source file in step with its edits, through the SAF grant it was imported with. */
    @Provides
    fun providePlaylistFileSync(
        @ApplicationContext context: Context,
        database: MediaDatabase
    ): PlaylistFileSync = SafPlaylistFileSync(context, database.songDataDao())
}
