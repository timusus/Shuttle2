package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.provider.emby.EmbyStreamUrlProvider
import com.simplecityapps.provider.jellyfin.JellyfinStreamUrlProvider
import com.simplecityapps.provider.plex.PlexStreamUrlProvider
import com.simplecityapps.provider.subsonic.SubsonicStreamUrlProvider
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.settings.DownloadSettings
import com.simplecityapps.shuttle.shared.IosStorage
import com.simplecityapps.shuttle.shared.downloads.DownloadRequests
import com.simplecityapps.shuttle.shared.downloads.OfflineDownloads
import com.simplecityapps.shuttle.shared.downloads.UrlSessionDownloads
import com.simplecityapps.shuttle.shared.network.ServerRequestPolicy
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.MainScope

/** Offline downloads of server songs (docs/architecture/downloads.md): [OfflineDownloads] over a background `URLSession`. */
@ContributesTo(AppScope::class)
@BindingContainer
class IosDownloadsModule {
    /** One per app: iOS allows only one session with the background identifier. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideUrlSessionDownloads(
        storage: IosStorage,
        serverRequestPolicy: ServerRequestPolicy
    ): UrlSessionDownloads = UrlSessionDownloads(storage.isolatedName, serverRequestPolicy)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOfflineDownloads(
        jellyfin: JellyfinStreamUrlProvider,
        emby: EmbyStreamUrlProvider,
        plex: PlexStreamUrlProvider,
        subsonic: SubsonicStreamUrlProvider,
        transport: UrlSessionDownloads,
        keyValueStore: KeyValueStore,
        downloadSettings: DownloadSettings,
        songRepository: SongRepository
    ): OfflineDownloads = OfflineDownloads(
        streamUrls = listOf(jellyfin, emby, plex, subsonic),
        transport = transport,
        requests = DownloadRequests(keyValueStore),
        // The main thread, where the transport is called from
        scope = MainScope(),
        wifiOnly = { downloadSettings.wifiOnly.value },
        loadSongs = { ids -> songRepository.loadSongs(SongQuery.SongIds(ids)) }
    )

    @Provides
    fun provideSongDownloader(downloads: OfflineDownloads): SongDownloader = downloads
}
