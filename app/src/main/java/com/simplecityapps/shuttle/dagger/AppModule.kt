package com.simplecityapps.shuttle.dagger

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.LruCache
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.ThemeManager
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [AppModuleBinds::class])
class AppModule {

    @Singleton
    @Provides
    fun provideDebugLoggingTree(@ApplicationContext context: Context, generalPreferenceManager: GeneralPreferenceManager): DebugLoggingTree {
        return DebugLoggingTree(context, generalPreferenceManager)
    }

    @Singleton
    @Provides
    fun provideArtworkCache(): LruCache<String, Bitmap> {
        return object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.allocationByteCount
            }
        }
    }

    @Singleton
    @Provides
    fun coroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable)
        }
    }

    @Singleton
    @Provides
    @Named("AppSupervisorJob")
    fun appSupervisorJob(): Job {
        return SupervisorJob()
    }

    @Singleton
    @Provides
    @Named("AppCoroutineScope")
    fun provideAppCoroutineScope(@Named("AppSupervisorJob") job: Job, coroutineExceptionHandler: CoroutineExceptionHandler): CoroutineScope {
        return CoroutineScope(Dispatchers.Main + job + coroutineExceptionHandler)
    }

    @Singleton
    @Provides
    fun provideSortPreferenceManager(preference: SharedPreferences): SortPreferenceManager {
        return SortPreferenceManager(preference)
    }

    @Singleton
    @Provides
    fun provideThemeManager(preferenceManager: GeneralPreferenceManager): ThemeManager {
        return ThemeManager(preferenceManager)
    }
}