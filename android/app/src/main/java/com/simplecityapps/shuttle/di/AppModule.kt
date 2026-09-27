package com.simplecityapps.shuttle.di

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.simplecityapps.shuttle.debug.DebugLoggingTree
import com.simplecityapps.shuttle.debug.livelog.LiveLogSink
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.settings.AppearanceSettings
import com.simplecityapps.shuttle.settings.DebugSettings
import com.simplecityapps.shuttle.ui.ThemeManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.time.Clock
import java.util.*

@ContributesTo(AppScope::class)
@BindingContainer
object AppModule {
    @SingleIn(AppScope::class)
    @Provides
    fun provideDebugLoggingTree(
        @ApplicationContext context: Context,
        debugSettings: DebugSettings,
        liveLogSink: Optional<LiveLogSink> = Optional.empty()
    ): DebugLoggingTree = DebugLoggingTree(context, debugSettings, liveLogSink)

    @SingleIn(AppScope::class)
    @Provides
    fun provideArtworkCache(): LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(
            key: String,
            value: Bitmap
        ): Int = value.allocationByteCount
    }

    @SingleIn(AppScope::class)
    @Provides
    fun provideThemeManager(appearanceSettings: AppearanceSettings): ThemeManager = ThemeManager(appearanceSettings)

    @SingleIn(AppScope::class)
    @Provides
    @Named("randomSeed")
    fun provideRandomSeed(): Long = Random().nextLong()

    @Provides
    fun provideRandom(): kotlin.random.Random = kotlin.random.Random.Default

    @Provides
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
