package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.shuttle.model.MediaProviderType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class MediaProviderViewModel @Inject constructor(
    private val mediaImporter: MediaImporter,
    private val plexMediaProvider: PlexMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider
) : ViewModel() {

    private val _mediaProviders = MutableStateFlow(listOf(MediaProviderType.MediaStore))
    val mediaProviders = _mediaProviders.asStateFlow()

    val unAddedMediaProviders = _mediaProviders
        .map { providers ->
            val addedProviders = providers.toSet()
            MediaProviderType.entries.filter { provider -> provider !in addedProviders }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed()
        )

    private val _showAddMediaProviderDialog = MutableStateFlow(false)
    val showAddMediaProviderDialog = _showAddMediaProviderDialog.asStateFlow()

    private val _showProviderOverflowMenu = MutableStateFlow<MediaProviderType?>(null)
    val showProviderOverflowMenu = _showProviderOverflowMenu.asStateFlow()

    fun onAddMediaProvider(provider: MediaProviderType) {
        mediaImporter.mediaProviders += provider.toMediaProvider()
        _mediaProviders.update { providers -> providers + provider }
        _showAddMediaProviderDialog.update { false }
    }

    fun onRemoveMediaProvider() {
        val provider = _showProviderOverflowMenu.value!!
        mediaImporter.mediaProviders -= provider.toMediaProvider()
        _mediaProviders.update { providers -> providers - provider }
        _showProviderOverflowMenu.update { null }
    }

    fun onAddMediaProviderClicked() {
        _showAddMediaProviderDialog.update { true }
    }

    fun onDismissAddMediaProviderRequest() {
        _showAddMediaProviderDialog.update { false }
    }

    fun onMediaProviderOverflowMenuClicked(provider: MediaProviderType) {
        _showProviderOverflowMenu.update { provider }
    }

    fun onDismissMediaProviderOverflowMenu() {
        _showProviderOverflowMenu.update { null }
    }

    private fun MediaProviderType.toMediaProvider(): MediaProvider = when (this) {
        MediaProviderType.MediaStore -> mediaStoreMediaProvider
        MediaProviderType.Shuttle -> taglibMediaProvider
        MediaProviderType.Emby -> embyMediaProvider
        MediaProviderType.Jellyfin -> jellyfinMediaProvider
        MediaProviderType.Plex -> plexMediaProvider
    }
}
