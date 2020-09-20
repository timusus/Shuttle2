package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import timber.log.Timber
import javax.inject.Inject

class MediaProviderSelectionFragment :
    Fragment(),
    Injectable,
    OnboardingChild,
    MediaProviderSelectionContract.View {

    private var radioGroup: RadioGroup by autoCleared()
    private var basicRadioButton: RadioButton by autoCleared()
    private var advancedRadioButton: RadioButton by autoCleared()
    private var toolbar: Toolbar by autoCleared()

    private var warningLabel: TextView by autoCleared()

    private var isOnboarding = true

    @Inject lateinit var presenterFactory: MediaProviderSelectionPresenter.Factory
    private lateinit var presenter: MediaProviderSelectionPresenter


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isOnboarding = requireArguments().getBoolean(ARG_ONBOARDING)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_provider_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        radioGroup = view.findViewById(R.id.radioGroup)
        basicRadioButton = view.findViewById(R.id.basic)
        advancedRadioButton = view.findViewById(R.id.advanced)

        toolbar = view.findViewById(R.id.toolbar)

        warningLabel = view.findViewById(R.id.warningLabel)

        val subtitleLabel: TextView = view.findViewById(R.id.subtitleLabel)
        if (isOnboarding) {
            subtitleLabel.text = "Shuttle can find your music using two different modes. You can change this later."
        } else {
            subtitleLabel.text = "Shuttle can find your music using two different modes:"
        }

        val moreInfoButton: Button = view.findViewById(R.id.moreInfoButton)
        moreInfoButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Media Provider")
                .setMessage("• Shuttle File Scanner\n\nSearches for music in directories you specify, and scans the files for metadata. More accurate than the Android Media Store, and allows you to modify/delete songs. \n\n• Android Media Store\n\nFaster, and easier to set up, but less reliable.")
                .setNegativeButton("Close", null)
                .show()
        }

        presenter = presenterFactory.create(isOnboarding)
        presenter.bindView(this)

        // Called after bind view, so our radio group can have its selection set without triggering the change listener
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            presenter.setSongProvider(
                when (checkedId) {
                    R.id.basic -> PlaybackPreferenceManager.SongProvider.MediaStore
                    R.id.advanced -> PlaybackPreferenceManager.SongProvider.TagLib
                    else -> PlaybackPreferenceManager.SongProvider.TagLib
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()

        if (isOnboarding) {
            toolbar.title = "Discover your music"
            toolbar.navigationIcon = null
        } else {
            toolbar.title = "Media provider"
            toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
        }

        // It seems we need some sort of arbitrary delay, to ensure the parent fragment has indeed finished its onViewCreated() and instantiated the next button.
        view?.postDelayed({
            getParent()?.let { parent ->
                parent.hideBackButton()
                parent.toggleNextButton(true)
                parent.showNextButton("Next")
            } ?: Timber.e("Failed to update state - getParent() returned null")
        }, 50)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // MediaProviderSelectionContract.View Implementation

    override fun setSongProvider(songProvider: PlaybackPreferenceManager.SongProvider) {
        when (songProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> radioGroup.check(R.id.basic)
            PlaybackPreferenceManager.SongProvider.TagLib -> radioGroup.check(R.id.advanced)
        }
    }

    override fun updateViewPager(songProvider: PlaybackPreferenceManager.SongProvider) {
        when (songProvider) {
            PlaybackPreferenceManager.SongProvider.MediaStore -> {
                radioGroup.check(R.id.basic)
                getParent()?.let { parent ->
                    val pages = parent.getPages().toMutableList()
                    if (pages.contains(OnboardingPage.MusicDirectories)) {
                        pages.remove(OnboardingPage.MusicDirectories)
                        parent.setPages(pages)
                    }
                }
            }
            PlaybackPreferenceManager.SongProvider.TagLib -> {
                radioGroup.check(R.id.advanced)
                getParent()?.let { parent ->
                    val pages = parent.getPages().toMutableList()
                    if (!pages.contains(OnboardingPage.MusicDirectories)) {
                        pages.add(pages.indexOf(OnboardingPage.Scanner), OnboardingPage.MusicDirectories)
                        parent.setPages(pages)
                    }
                }
            }
        }
    }

    override fun showChangeSongProviderWarning(show: Boolean) {
        warningLabel.isVisible = show
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.MediaProviderSelector

    override fun getParent(): OnboardingParent? {
        return parentFragment as? OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent()?.goToNext() ?: Timber.e("Failed to goToNext() - getParent() returned null")
    }


    // Static

    companion object {
        const val ARG_ONBOARDING = "is_onboarding"
        fun newInstance(isOnboarding: Boolean = true): MediaProviderSelectionFragment {
            return MediaProviderSelectionFragment().withArgs { putBoolean(ARG_ONBOARDING, isOnboarding) }
        }
    }
}