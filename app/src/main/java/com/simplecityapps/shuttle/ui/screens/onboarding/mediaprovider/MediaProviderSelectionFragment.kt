package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import javax.inject.Inject

class MediaProviderSelectionFragment :
    Fragment(),
    Injectable,
    OnboardingChild {

    private lateinit var radioGroup: RadioGroup
    private lateinit var basicRadioButton: RadioButton
    private lateinit var advancedRadioButton: RadioButton

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    private var isOnboarding = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isOnboarding = arguments!!.getBoolean(ARG_ONBOARDING)
    }

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_provider_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroup = view.findViewById(R.id.radioGroup)
        basicRadioButton = view.findViewById(R.id.basic)
        advancedRadioButton = view.findViewById(R.id.advanced)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        if (isOnboarding) {
            toolbar.title = "Discover your music"
            toolbar.navigationIcon = null
        } else {
            toolbar.title = "Media provider"
            toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
        }

        val subtitleLabel: TextView = view.findViewById(R.id.subtitleLabel)
        if (isOnboarding) {
            subtitleLabel.text = "Shuttle can find your music using two different modes. You can change this later."
        } else {
            subtitleLabel.text = "Shuttle can find your music using two different modes:"
        }

        getParent().showNextButton("Next")

        val initialSongProvider = playbackPreferenceManager.songProvider
        when (initialSongProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                radioGroup.check(R.id.basic)
            }
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                radioGroup.check(R.id.advanced)
            }
        }

        val warningLabel: TextView = view.findViewById(R.id.warningLabel)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val pages = getParent().getPages().toMutableList()

            when (checkedId) {
                R.id.basic -> {
                    playbackPreferenceManager.songProvider = PlaybackPreferenceManager.SongProvider.MediaStore
                    getParent().uriMimeTypePairs = null
                    pages.remove(OnboardingPage.MusicDirectories)
                    getParent().setPages(pages)
                }
                R.id.advanced -> {
                    playbackPreferenceManager.songProvider = PlaybackPreferenceManager.SongProvider.TagLib
                    if (!pages.contains(OnboardingPage.MusicDirectories)) {
                        pages.add(pages.indexOf(OnboardingPage.Scanner), OnboardingPage.MusicDirectories)
                        getParent().setPages(pages)
                    }
                }
            }

            if (!isOnboarding) {
                warningLabel.isVisible = playbackPreferenceManager.songProvider != initialSongProvider
            }
        }
    }

    override fun onResume() {
        super.onResume()

        getParent().hideBackButton()
        getParent().toggleNextButton(true)
        getParent().showNextButton("Next")
    }

    override fun onDestroyView() {
        radioGroup.setOnCheckedChangeListener(null)
        super.onDestroyView()
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.MediaProviderSelector

    override fun getParent() = parentFragment as OnboardingParent

    override fun handleNextButtonClick() {
        getParent().goToNext()
    }


    // Static

    companion object {
        const val ARG_ONBOARDING = "is_onboarding"
        fun newInstance(isOnboarding: Boolean = true): MediaProviderSelectionFragment {
            return MediaProviderSelectionFragment().withArgs { putBoolean(ARG_ONBOARDING, isOnboarding) }
        }
    }
}