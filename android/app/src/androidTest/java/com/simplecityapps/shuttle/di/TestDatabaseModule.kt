package com.simplecityapps.shuttle.di

import android.content.Context
import androidx.room.Room
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
class TestDatabaseModule {
    @Provides
    @Singleton
    fun provideMediaDatabase(
        @ApplicationContext context: Context
    ): MediaDatabase = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
