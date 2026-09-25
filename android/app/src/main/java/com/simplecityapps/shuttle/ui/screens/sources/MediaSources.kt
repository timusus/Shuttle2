package com.simplecityapps.shuttle.ui.screens.sources

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The media providers the library imports from, and the scan that imports them. The enabled set persists in
 * [PlaybackPreferenceManager.mediaProviderTypes] and is mirrored into [MediaImporter.mediaProviders].
 */
interface MediaSources {
    val enabledTypes: StateFlow<List<MediaProviderType>>

    fun enable(type: MediaProviderType)

    /** Stops importing from [type] and removes its songs and playlists from the library and the queue. */
    fun disable(type: MediaProviderType)

    /** Imports from every enabled provider, outliving the screen that asked; a no-op while an import runs. */
    fun scan()

    /** Scans, turning the S2 scanner on first if no source on this device is: what a music permission grant starts. */
    fun scanThisDevice() {
        if (enabledTypes.value.none { it.isLocal }) enable(MediaProviderType.Shuttle)
        scan()
    }
}

/** Songs on this device come from the S2 scanner, or the Android (MediaStore) provider for users who chose it before. */
val MediaProviderType.isLocal: Boolean get() = this == MediaProviderType.Shuttle || this == MediaProviderType.MediaStore

@Singleton
class DefaultMediaSources @Inject constructor(
    private val preferences: PlaybackPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val plexMediaProvider: PlexMediaProvider,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueManager: QueueOperations,
    private val playbackManager: PlaybackOperations,
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
        mediaImporter.mediaProviders -= type.provider()

        if (queueManager.getCurrentItem()?.song?.mediaProvider == type) playbackManager.pause()
        queueManager.remove(queueManager.getQueue().filter { it.song.mediaProvider == type })
        appCoroutineScope.launch {
            songRepository.removeAll(type)
            playlistRepository.deleteAll(type)
        }
    }

    override fun scan() {
        appCoroutineScope.launch { mediaImporter.import() }
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
    }
}
