package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.data.room.database.trackingIdentityChanges
import com.simplecityapps.shuttle.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class, replaces = [DatabaseModule::class])
class TestDatabaseModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaDatabase(
        @ApplicationContext context: Context
    ): MediaDatabase = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java).trackingIdentityChanges()
        .allowMainThreadQueries()
        .build()
}
