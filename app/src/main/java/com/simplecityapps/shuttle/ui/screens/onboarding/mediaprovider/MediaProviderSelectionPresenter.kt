package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject

interface MediaProviderSelectionContract {

    interface Presenter {
        fun setSongProvider(songProvider: PlaybackPreferenceManager.SongProvider)
    }

    interface View {
        fun setSongProvider(songProvider: PlaybackPreferenceManager.SongProvider)
        fun updateViewPager(songProvider: PlaybackPreferenceManager.SongProvider)
        fun showChangeSongProviderWarning(show: Boolean)
    }
}

class MediaProviderSelectionPresenter @AssistedInject constructor(
    @Assisted private val isOnboarding: Boolean,
    private val playbackPreferenceManager: PlaybackPreferenceManager,
    private val mediaImporter: MediaImporter,
    private val taglibSongProvider: TaglibSongProvider,
    private val mediaStoreSongProvider: MediaStoreSongProvider
) : BasePresenter<MediaProviderSelectionContract.View>(),
    MediaProviderSelectionContract.Presenter {

    @AssistedInject.Factory
    interface Factory {
        fun create(isOnboarding: Boolean): MediaProviderSelectionPresenter
    }

    private var initialSongProvider = playbackPreferenceManager.songProvider

    override fun bindView(view: MediaProviderSelectionContract.View) {
        super.bindView(view)

        view.setSongProvider(playbackPreferenceManager.songProvider)
    }

    override fun setSongProvider(songProvider: PlaybackPreferenceManager.SongProvider) {
        playbackPreferenceManager.songProvider = songProvider

        mediaImporter.mediaProvider = when (songProvider) {
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                taglibSongProvider
            }
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                mediaStoreSongProvider
            }
        }

        view?.showChangeSongProviderWarning(!isOnboarding && songProvider != initialSongProvider)
        view?.updateViewPager(songProvider)
    }
}