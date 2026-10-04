package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.SyncTrigger
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
import com.simplecityapps.shuttle.shared.local.IosLocalMediaProvider
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The library's sources on iOS: this device's files (the S2 scanner, [MediaProviderType.Shuttle]), Jellyfin, Emby
 * and Plex. The iOS counterpart of Android's `DefaultMediaSources`, saving the enabled types under
 * the same key; a fresh install reads this device's files, as Android's scanner starts on. Hands the saved providers to
 * the importer when it's created.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMediaSources @Inject constructor(
    private val store: KeyValueStore,
    private val generalPreferences: GeneralPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val localMediaProvider: IosLocalMediaProvider,
    private val plexMediaProvider: PlexMediaProvider,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueOperations: QueueOperations,
    private val playbackOperations: PlaybackOperations,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : MediaSources {
    private val _enabledTypes = MutableStateFlow(savedTypes())
    override val enabledTypes: StateFlow<List<MediaProviderType>> = _enabledTypes.asStateFlow()

    init {
        _enabledTypes.value.forEach { type -> type.provider()?.let { mediaImporter.mediaProviders += it } }
    }

    override fun enable(type: MediaProviderType) {
        val provider = type.provider() ?: return
        if (type !in _enabledTypes.value) save(_enabledTypes.value + type)
        mediaImporter.mediaProviders += provider
    }

    override fun disable(type: MediaProviderType) {
        if (type in _enabledTypes.value) save(_enabledTypes.value - type)
        type.provider()?.let { mediaImporter.mediaProviders -= it }

        if (queueOperations.getCurrentItem()?.song?.mediaProvider == type) playbackOperations.pause()
        queueOperations.remove(queueOperations.getQueue().filter { it.song.mediaProvider == type })
        appCoroutineScope.launch {
            songRepository.removeAll(type)
            playlistRepository.deleteAll(type)
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

    private fun savedTypes(): List<MediaProviderType> = (store.getString(KEY, null) ?: MediaProviderType.Shuttle.ordinal.toString())
        .split(",")
        .filter { it.isNotEmpty() }
        .map { MediaProviderType.init(it.toInt()) }
        .filter { it.provider() != null }

    private fun save(types: List<MediaProviderType>) {
        store.putString(KEY, types.joinToString(",") { it.ordinal.toString() })
        _enabledTypes.value = types
    }

    private fun MediaProviderType.provider(): MediaProvider? = when (this) {
        MediaProviderType.Shuttle -> localMediaProvider
        MediaProviderType.Jellyfin -> jellyfinMediaProvider
        MediaProviderType.Emby -> embyMediaProvider
        MediaProviderType.Plex -> plexMediaProvider
        MediaProviderType.MediaStore -> null
    }

    private companion object {
        /** Android's `PlaybackPreferenceManager.mediaProviderTypes` key: ordinals, comma separated. */
        const val KEY = "media_providers"
    }
}
