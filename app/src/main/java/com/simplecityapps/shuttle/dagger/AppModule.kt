package com.simplecityapps.shuttle.dagger

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.LruCache
import com.simplecityapps.shuttle.ShuttleApplication
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.ui.screens.library.SortPreferenceManager
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Named

@Module(includes = [AppModuleBinds::class])
class AppModule {

    @AppScope
    @Provides
    fun provideContext(application: ShuttleApplication): Context {
        return application.applicationContext
    }

    @AppScope
    @Provides
    fun provideDebugLoggingTree(context: Context, sharedPreferences: SharedPreferences): DebugLoggingTree {
        return DebugLoggingTree(context, sharedPreferences)
    }

    @AppScope
    @Provides
    fun provideArtworkCache(): LruCache<String, Bitmap> {
        return object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.allocationByteCount
            }
        }
    }

    @AppScope
    @Provides
    fun coroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable)
        }
    }

    @AppScope
    @Provides
    @Named("AppSupervisorJob")
    fun appSupervisorJob(): Job {
        return SupervisorJob()
    }

    @AppScope
    @Provides
    @Named("AppCoroutineScope")
    fun provideAppCoroutineScope(@Named("AppSupervisorJob") job: Job, coroutineExceptionHandler: CoroutineExceptionHandler): CoroutineScope {
        return CoroutineScope(Dispatchers.Main + job + coroutineExceptionHandler)
    }

    @AppScope
    @Provides
    fun provideSortPreferenceManager(preference: SharedPreferences): SortPreferenceManager {
        return SortPreferenceManager(preference)
    }
}