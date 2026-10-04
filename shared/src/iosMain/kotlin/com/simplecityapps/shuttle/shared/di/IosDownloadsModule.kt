package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.provider.emby.EmbyStreamUrlProvider
import com.simplecityapps.provider.jellyfin.JellyfinStreamUrlProvider
import com.simplecityapps.provider.plex.PlexStreamUrlProvider
import com.simplecityapps.shuttle.shared.IosStorage
import com.simplecityapps.shuttle.shared.downloads.OfflineDownloads
import com.simplecityapps.shuttle.shared.downloads.UrlSessionDownloads
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Offline downloads of server songs (docs/architecture/downloads.md): [OfflineDownloads] over a background `URLSession`. */
@ContributesTo(AppScope::class)
@BindingContainer
class IosDownloadsModule {
    /** One per app: iOS allows only one session with the background identifier. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideUrlSessionDownloads(storage: IosStorage): UrlSessionDownloads = UrlSessionDownloads(storage.isolatedName)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOfflineDownloads(
        jellyfin: JellyfinStreamUrlProvider,
        emby: EmbyStreamUrlProvider,
        plex: PlexStreamUrlProvider,
        transport: UrlSessionDownloads
    ): OfflineDownloads = OfflineDownloads(listOf(jellyfin, emby, plex), transport)

    @Provides
    fun provideSongDownloader(downloads: OfflineDownloads): SongDownloader = downloads
}
