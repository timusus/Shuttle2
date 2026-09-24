package com.simplecityapps.shuttle.downloads.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.simplecityapps.shuttle.downloads.DefaultSongDownloadManager
import com.simplecityapps.shuttle.downloads.DefaultSongDownloadRepository
import com.simplecityapps.shuttle.downloads.DownloadPreferences
import com.simplecityapps.shuttle.downloads.SongDownloadManager
import com.simplecityapps.shuttle.downloads.SongDownloadRepository
import com.simplecityapps.shuttle.downloads.downloadRequirements
import com.simplecityapps.shuttle.downloads.runOnMainThreadBlocking
import com.simplecityapps.shuttle.downloads.service.SongDownloadService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import timber.log.Timber

/** The cache downloaded songs live in: app-private, never evicted. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@UnstableApi
@InstallIn(SingletonComponent::class)
@Module
abstract class DownloadsModule {
    @Binds
    abstract fun bindSongDownloadManager(impl: DefaultSongDownloadManager): SongDownloadManager

    @Binds
    abstract fun bindSongDownloadRepository(impl: DefaultSongDownloadRepository): SongDownloadRepository

    companion object {
        private const val MAX_PARALLEL_DOWNLOADS = 2
        private const val TIMEOUT_MS = 30_000

        @Provides
        @Singleton
        fun provideDatabaseProvider(
            @ApplicationContext context: Context
        ): DatabaseProvider = StandaloneDatabaseProvider(context)

        /**
         * In filesDir, not cacheDir, so the system never clears it, and with [NoOpCacheEvictor] so
         * nothing is ever evicted: a download only goes when it's removed.
         */
        @Provides
        @Singleton
        @DownloadCache
        fun provideDownloadCache(
            @ApplicationContext context: Context,
            databaseProvider: DatabaseProvider
        ): Cache {
            val directory = File(context.filesDir, "downloads")
            return try {
                SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)
            } catch (e: Throwable) {
                // A corrupt cache index surfaces as ExceptionInInitializerError, an Error rather than
                // an Exception, hence Throwable. Starting over loses every download, but the
                // alternative is a crash on every launch.
                Timber.e(e, "Download cache is corrupt; deleting it and every download in it")
                directory.deleteRecursively()
                SimpleCache(directory, NoOpCacheEvictor(), databaseProvider)
            }
        }

        @Provides
        @Singleton
        fun provideDownloadManager(
            @ApplicationContext context: Context,
            databaseProvider: DatabaseProvider,
            @DownloadCache cache: Cache,
            downloadPreferences: DownloadPreferences
        ): DownloadManager {
            val dataSourceFactory =
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(TIMEOUT_MS)
                    .setReadTimeoutMs(TIMEOUT_MS)
                    .setAllowCrossProtocolRedirects(true)
            // Media3's DownloadManager binds to whatever thread's Looper is current when it's
            // constructed (falling back to main only if the calling thread has none at all), and
            // every later call must come from that same thread. Hilt resolves this singleton
            // wherever it's first injected, which isn't guaranteed to be main, so force
            // construction onto main here rather than depending on caller discipline.
            return runOnMainThreadBlocking {
                DownloadManager(context, databaseProvider, cache, dataSourceFactory, Runnable::run).apply {
                    maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
                    // Built with the preference as it stands, so DownloadRequirementsManager only
                    // has to push a change (each push starts the foreground service).
                    requirements = downloadRequirements(downloadPreferences.wifiOnly)
                }
            }
        }

        @Provides
        @Singleton
        fun provideDownloadNotificationHelper(
            @ApplicationContext context: Context
        ): DownloadNotificationHelper = DownloadNotificationHelper(context, SongDownloadService.CHANNEL_ID)
    }
}
