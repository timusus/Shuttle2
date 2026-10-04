package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.provider.TagReadGuard
import com.simplecityapps.localmediaprovider.local.provider.mediastore.KTagLibMediaStoreTagReader
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreTagReader
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter.Companion.songTagsOutdated
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.sources.SafScannerFolderStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
class MediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFileScanner(): FileScanner = FileScanner()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTagReadGuard(
        @ApplicationContext context: Context,
        preferenceManager: GeneralPreferenceManager
    ): TagReadGuard = TagReadGuard(context, preferenceManager)

    @Provides
    @SingleIn(AppScope::class)
    fun provideKTagLib(): KTagLib = KTagLib()

    @Provides
    @SingleIn(AppScope::class)
    fun provideTagLibSongProvider(
        @ApplicationContext context: Context,
        kTagLib: KTagLib,
        fileScanner: FileScanner,
        tagReadGuard: TagReadGuard,
        folderStore: SafScannerFolderStore,
        preferenceManager: GeneralPreferenceManager
    ): TaglibMediaProvider = TaglibMediaProvider(
        context,
        kTagLib,
        fileScanner,
        tagReadGuard,
        backfillFileTags = { preferenceManager.songTagsOutdated(MediaProviderType.Shuttle) },
        folders = folderStore::scannerFolders
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaStoreTagReader(
        @ApplicationContext context: Context,
        kTagLib: KTagLib,
        tagReadGuard: TagReadGuard
    ): MediaStoreTagReader = KTagLibMediaStoreTagReader(context, kTagLib, tagReadGuard)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaStoreSongProvider(
        @ApplicationContext context: Context,
        tagReader: MediaStoreTagReader,
        preferenceManager: GeneralPreferenceManager,
        tagReadGuard: TagReadGuard
    ): MediaStoreMediaProvider = MediaStoreMediaProvider(context, tagReader, preferenceManager, tagReadGuard)
}
