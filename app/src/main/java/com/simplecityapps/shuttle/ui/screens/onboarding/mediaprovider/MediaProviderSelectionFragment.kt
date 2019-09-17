package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import javax.inject.Inject

class MediaProviderSelectionFragment : Fragment(), Injectable, OnboardingChild {

    private lateinit var radioGroup: RadioGroup
    private lateinit var basicRadioButton: RadioButton
    private lateinit var advancedRadioButton: RadioButton

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_provider_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroup = view.findViewById(R.id.radioGroup)
        basicRadioButton = view.findViewById(R.id.basic)
        advancedRadioButton = view.findViewById(R.id.advanced)

        getParent().showNextButton("Next")

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
}