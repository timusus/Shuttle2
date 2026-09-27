package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackOperations
import com.simplecityapps.playback.queue.QueueOperations
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.KeyValueStore
import com.simplecityapps.shuttle.persistence.putString
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
 * The library's sources on iOS: Jellyfin and Emby for now (Plex and this device's files come in later phases). The
 * iOS counterpart of Android's `DefaultMediaSources`, saving the enabled types under the same key; a fresh install
 * has none, as there's no scanner to start with. Hands the saved providers to the importer when it's created.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosMediaSources @Inject constructor(
    private val store: KeyValueStore,
    private val generalPreferences: GeneralPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
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

    override fun scan() {
        appCoroutineScope.launch { mediaImporter.import() }
    }

    /** Nothing on this device to scan until iOS has a local provider. */
    override fun scanThisDevice() = Unit

    private fun savedTypes(): List<MediaProviderType> = store.getString(KEY, "")!!
        .split(",")
        .filter { it.isNotEmpty() }
        .map { MediaProviderType.init(it.toInt()) }
        .filter { it.provider() != null }

    private fun save(types: List<MediaProviderType>) {
        store.putString(KEY, types.joinToString(",") { it.ordinal.toString() })
        _enabledTypes.value = types
    }

    private fun MediaProviderType.provider(): MediaProvider? = when (this) {
        MediaProviderType.Jellyfin -> jellyfinMediaProvider
        MediaProviderType.Emby -> embyMediaProvider
        MediaProviderType.Shuttle, MediaProviderType.MediaStore, MediaProviderType.Plex -> null
    }

    private companion object {
        /** Android's `PlaybackPreferenceManager.mediaProviderTypes` key: ordinals, comma separated. */
        const val KEY = "media_providers"
    }
}
