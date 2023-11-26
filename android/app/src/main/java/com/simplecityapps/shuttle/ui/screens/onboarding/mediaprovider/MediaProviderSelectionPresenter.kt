package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreMediaProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibMediaProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.mediaprovider.repository.playlists.PlaylistRepository
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.provider.emby.EmbyMediaProvider
import com.simplecityapps.provider.jellyfin.JellyfinMediaProvider
import com.simplecityapps.provider.plex.PlexMediaProvider
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface MediaProviderSelectionContract {

    interface Presenter {
        fun addProviderClicked()
        fun addMediaProviderType(mediaProviderType: MediaProviderType)
        fun removeMediaProviderType(mediaProviderType: MediaProviderType)
    }

    interface View {
        fun showMediaProviderSelectionDialog(mediaProviderTypes: List<MediaProviderType>)
        fun setMediaProviders(mediaProviderTypes: List<MediaProviderType>)
    }
}

class MediaProviderSelectionPresenter @AssistedInject constructor(
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val taglibMediaProvider: TaglibMediaProvider,
    private val mediaStoreMediaProvider: MediaStoreMediaProvider,
    private val embyMediaProvider: EmbyMediaProvider,
    private val jellyfinMediaProvider: JellyfinMediaProvider,
    private val plexMediaProvider: PlexMediaProvider,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueManager: QueueManager,
    private val playbackManager: PlaybackManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @Assisted private val isOnboarding: Boolean
) : BasePresenter<MediaProviderSelectionContract.View>(),
    MediaProviderSelectionContract.Presenter {

    @AssistedFactory
    interface Factory {
        fun create(isOnboarding: Boolean): MediaProviderSelectionPresenter
    }

    override fun bindView(view: MediaProviderSelectionContract.View) {
        super.bindView(view)

        val mediaProviders = playbackPreferenceManager.mediaProviderTypes.toMutableList()
        view.setMediaProviders(mediaProviders)

        if (isOnboarding && mediaProviders.isEmpty()) {
            addMediaProviderType(MediaProviderType.MediaStore)
        }
    }

    override fun addProviderClicked() {
        view?.showMediaProviderSelectionDialog(
            (MediaProviderType.values().toList() - playbackPreferenceManager.mediaProviderTypes)
        )
    }

    override fun addMediaProviderType(mediaProviderType: MediaProviderType) {
        if (!playbackPreferenceManager.mediaProviderTypes.contains(mediaProviderType)) {
            playbackPreferenceManager.mediaProviderTypes = playbackPreferenceManager.mediaProviderTypes + mediaProviderType
        }

        mediaImporter.mediaProviders += mediaProviderType.toMediaProvider()

        view?.setMediaProviders(playbackPreferenceManager.mediaProviderTypes)
    }

    override fun removeMediaProviderType(mediaProviderType: MediaProviderType) {
        if (playbackPreferenceManager.mediaProviderTypes.contains(mediaProviderType)) {
            playbackPreferenceManager.mediaProviderTypes = playbackPreferenceManager.mediaProviderTypes - mediaProviderType
        }

        mediaImporter.mediaProviders -= mediaProviderType.toMediaProvider()

        view?.setMediaProviders(playbackPreferenceManager.mediaProviderTypes)

        queueManager.getCurrentItem()?.let {
            if (it.song.mediaProvider == mediaProviderType) {
                playbackManager.pause()
            }
        }
        queueManager.remove(queueManager.getQueue().filter { it.song.mediaProvider == mediaProviderType })

        appCoroutineScope.launch {
            songRepository.removeAll(mediaProviderType)
            playlistRepository.deleteAll(mediaProviderType)
        }
    }

    private fun MediaProviderType.toMediaProvider(): MediaProvider {
        return when (this) {
            MediaProviderType.MediaStore -> mediaStoreMediaProvider
            MediaProviderType.Shuttle -> taglibMediaProvider
            MediaProviderType.Emby -> embyMediaProvider
            MediaProviderType.Jellyfin -> jellyfinMediaProvider
            MediaProviderType.Plex -> plexMediaProvider
        }
    }
}
