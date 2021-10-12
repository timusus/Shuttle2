package com.simplecityapps.shuttle.di;

import android.content.Context
import com.simplecityapps.shuttle.common.database.DefaultSongSharedDatabase
import com.simplecityapps.shuttle.common.database.SongDatabaseDriver
import com.simplecityapps.shuttle.common.database.SongSharedDatabase
import com.simplecityapps.shuttle.repository.SongRepository
import com.squareup.sqldelight.db.SqlDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class MediaProviderModule {


}