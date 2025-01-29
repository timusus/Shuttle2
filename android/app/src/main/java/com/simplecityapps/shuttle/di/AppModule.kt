package com.simplecityapps.shuttle.di

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
import java.util.*
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module(includes = [AppModuleBinds::class])
class AppModule {
    @Singleton
    @Provides
    fun provideDebugLoggingTree(
        @ApplicationContext context: Context,
        generalPreferenceManager: GeneralPreferenceManager
    ): DebugLoggingTree = DebugLoggingTree(context, generalPreferenceManager)

    @Singleton
    @Provides
    fun provideArtworkCache(): LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(
            key: String,
            value: Bitmap
        ): Int = value.allocationByteCount
    }

    @Singleton
    @Provides
    fun provideSortPreferenceManager(preference: SharedPreferences): SortPreferenceManager = SortPreferenceManager(preference)

    @Singleton
    @Provides
    fun provideThemeManager(preferenceManager: GeneralPreferenceManager): ThemeManager = ThemeManager(preferenceManager)

    @Singleton
    @Provides
    @Named("randomSeed")
    fun provideRandomSeed(): Long = Random().nextLong()
}
