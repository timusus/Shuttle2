package com.simplecityapps.shuttle.di

import android.content.Context
import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.localmediaprovider.local.data.room.database.MediaDatabase
import com.simplecityapps.localmediaprovider.local.provider.mediastore.KTagLibMediaStoreTagReader
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreTagReader
import com.simplecityapps.localmediaprovider.local.provider.taglib.FileScanner
import com.simplecityapps.localmediaprovider.local.provider.taglib.MediaStoreAudioLister
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.TagReadGuard
import com.simplecityapps.mediaprovider.songTagsOutdated
import com.simplecityapps.shuttle.di.ApplicationContext
import com.simplecityapps.shuttle.di.IoDispatcher
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.SourceStateStore
import com.simplecityapps.shuttle.sources.SafScannerFolderStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher

@ContributesTo(AppScope::class)
@BindingContainer
class MediaProviderModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideFileScanner(
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): FileScanner = FileScanner(ioDispatcher)

    @Provides
    @SingleIn(AppScope::class)
    fun provideTagReadGuard(
        @ApplicationContext context: Context,
        sourceState: SourceStateStore
    ): TagReadGuard = TagReadGuard(context, sourceState)

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
        sourceState: SourceStateStore,
        database: MediaDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): TaglibMediaProvider = TaglibMediaProvider(
        context,
        kTagLib,
        fileScanner,
        tagReadGuard,
        ioDispatcher,
        backfillFileTags = { sourceState.songTagsOutdated(MediaProviderType.Shuttle) },
        mediaStoreFiles = MediaStoreAudioLister.incremental(context, database.mediaStoreFileDao(), MediaProviderType.Shuttle),
        folders = folderStore::scannerFolders
    )

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaStoreTagReader(
        @ApplicationContext context: Context,
        kTagLib: KTagLib,
        tagReadGuard: TagReadGuard,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MediaStoreTagReader = KTagLibMediaStoreTagReader(context, kTagLib, tagReadGuard, ioDispatcher)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMediaStoreSongProvider(
        @ApplicationContext context: Context,
        tagReader: MediaStoreTagReader,
        sourceState: SourceStateStore,
        tagReadGuard: TagReadGuard,
        database: MediaDatabase,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MediaStoreMediaProvider = MediaStoreMediaProvider(
        context,
        tagReader,
        sourceState,
        tagReadGuard,
        ioDispatcher,
        MediaStoreAudioLister.incremental(context, database.mediaStoreFileDao(), MediaProviderType.MediaStore)
    )
}
