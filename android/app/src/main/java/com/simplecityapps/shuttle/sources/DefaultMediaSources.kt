package com.simplecityapps.shuttle.sources

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.SyncTrigger
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.provider.subsonic.SubsonicMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.actions.SongDownloader
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SingleIn(AppScope::class)
class DefaultMediaSources @Inject constructor(
    private val preferences: PlaybackPreferenceManager,
    private val generalPreferences: GeneralPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val plexMediaProvider: PlexMediaProvider,
    private val subsonicMediaProvider: SubsonicMediaProvider,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    private val songDownloader: SongDownloader,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MediaSources {
    private val _enabledTypes = MutableStateFlow(preferences.mediaProviderTypes)
    override val enabledTypes: StateFlow<List<MediaProviderType>> = _enabledTypes.asStateFlow()

    /** Hands the saved providers to the importer, once at startup. */
    fun attachEnabled() {
        _enabledTypes.value.forEach { type -> mediaImporter.mediaProviders += type.provider() }
    }

    override fun enable(type: MediaProviderType) {
        if (type !in _enabledTypes.value) save(_enabledTypes.value + type)
        mediaImporter.mediaProviders += type.provider()
    }

    override fun disable(type: MediaProviderType) {
        if (type in _enabledTypes.value) save(_enabledTypes.value - type)

        if (queueOperations.getCurrentItem()?.song?.mediaProvider == type) playbackOperations.pause()
        queueOperations.remove(queueOperations.getQueue().filter { it.song.mediaProvider == type })
        // Undispatched, so the importer drops the source before this returns; its running import ends before its songs go,
        // so none it read are stored after them
        appCoroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mediaImporter.removeProvider(type.provider())
            songDownloader.removeAll(type)
            songRepository.removeAll(type)
            playlistRepository.deleteAll(type)
            generalPreferences.clearSourceState(type.name)
        }
    }

    override val hasScanned: Boolean get() = generalPreferences.lastMediaImportDate != null

    override val songTagsOutdated: Boolean get() = mediaImporter.songTagsOutdated

    override fun scan(foldersChanged: Boolean) {
        appCoroutineScope.launch { mediaImporter.import(foldersChanged) }
    }

    override fun syncIfStale() {
        appCoroutineScope.launch { mediaImporter.sync(SyncTrigger.Foreground) }
    }

    private fun save(types: List<MediaProviderType>) {
        preferences.mediaProviderTypes = types
        _enabledTypes.value = types
    }

    private fun MediaProviderType.provider(): MediaProvider = when (this) {
        MediaProviderType.Shuttle -> taglibMediaProvider
        MediaProviderType.MediaStore -> mediaStoreMediaProvider
        MediaProviderType.Emby -> embyMediaProvider
        MediaProviderType.Jellyfin -> jellyfinMediaProvider
        MediaProviderType.Plex -> plexMediaProvider
        MediaProviderType.Subsonic -> subsonicMediaProvider
    }
}
