package com.simplecityapps.shuttle.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.MediaProviderSelectionFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.scanner.MediaScannerFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.storage.StoragePermissionFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.taglib.DirectorySelectionFragment
import me.relex.circleindicator.CircleIndicator3
import javax.inject.Inject

enum class OnboardingPage {
    StoragePermission,
    MediaProviderSelector,
    MusicDirectories,
    Scanner
}

interface OnboardingParent {
    fun goToNext()
    fun goToPrevious()
    fun hideNextButton()
    fun showNextButton(text: String? = null)
    fun toggleNextButton(enabled: Boolean)
    fun hideBackButton()
    fun showBackButton(text: String? = null)
    fun getPages(): List<OnboardingPage>
    fun setPages(pages: List<OnboardingPage>)
    fun exit()
}

interface OnboardingChild {
    val page: OnboardingPage
    fun getParent(): OnboardingParent?
    fun handleNextButtonClick() {}
    fun handleBackButtonClick() {}
}

class OnboardingParentFragment : Fragment(),
    OnboardingParent,
    Injectable {

    private var viewPager: ViewPager2 by autoCleared()

    private lateinit var adapter: OnboardingAdapter

    private var nextButton: Button by autoCleared()
    private var previousButton: Button by autoCleared()

    private var indicator: CircleIndicator3 by autoCleared()

    private val args: OnboardingParentFragmentArgs by navArgs()

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager

    @Inject lateinit var generalPreferenceManager: GeneralPreferenceManager


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = OnboardingAdapter(this, args.isOnboarding)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false
        viewPager.clearAdapterOnDetach()

        indicator = view.findViewById(R.id.indicator)
        indicator.setViewPager(viewPager)
        adapter.registerAdapterDataObserver(indicator.adapterDataObserver)

        val pages = mutableListOf<OnboardingPage>()
        if (!hasStoragePermission()) {
            pages.add(OnboardingPage.StoragePermission)
        }
        pages.add(OnboardingPage.MediaProviderSelector)
        pages.add(OnboardingPage.Scanner)

        adapter.data = pages

        nextButton = view.findViewById(R.id.nextButton)
        nextButton.setOnClickListener {
            val currentPage = adapter.data[viewPager.currentItem]
            childFragmentManager.fragments.filterIsInstance<OnboardingChild>().firstOrNull { it.page == currentPage }?.handleNextButtonClick()
        }

        previousButton = view.findViewById(R.id.previousButton)
        previousButton.setOnClickListener {
            val currentPage = adapter.data[viewPager.currentItem]
            childFragmentManager.fragments.filterIsInstance<OnboardingChild>().firstOrNull { it.page == currentPage }?.handleBackButtonClick()
        }
    }


    // PageCompletionListener Implementation

    override fun getPages(): List<OnboardingPage> {
        return adapter.data
    }

    override fun setPages(pages: List<OnboardingPage>) {
        adapter.data = pages
        adapter.notifyDataSetChanged()
    }

    override fun goToNext() {
        if (viewPager.currentItem < adapter.data.size - 1) {
            viewPager.currentItem++
        }
    }

    override fun goToPrevious() {
        if (viewPager.currentItem > 0) {
            viewPager.currentItem--
        }
    }

    override fun hideNextButton() {
        nextButton.isVisible = false
    }

    override fun showNextButton(text: String?) {
        text?.let {
            nextButton.text = text
        }
        nextButton.isVisible = true
    }

    override fun toggleNextButton(enabled: Boolean) {
        nextButton.isEnabled = enabled
    }

    override fun hideBackButton() {
        previousButton.isVisible = false
    }

    override fun showBackButton(text: String?) {
        text?.let {
            previousButton.text = text
        }
        previousButton.isVisible = true
    }

    override fun exit() {
        generalPreferenceManager.hasOnboarded = true

        if (args.isOnboarding) {
            findNavController().navigate(R.id.action_onboardingFragment_to_mainFragment)
        } else {
            findNavController().popBackStack()
        }
    }


    // Private

    private fun hasStoragePermission(): Boolean {
        return (checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }


    // Static

    companion object {
        const val REQUEST_CODE_READ_STORAGE = 100

        const val TAG = "OnboardingParentFragment"
        fun newInstance(args: OnboardingParentFragmentArgs) = OnboardingParentFragment().apply {
            arguments = args.toBundle()
        }
    }

    class OnboardingAdapter(fragment: Fragment, private val isOnboarding: Boolean) : FragmentStateAdapter(fragment) {
        var data = listOf<OnboardingPage>()
            set(value) {
                if (field != value) {
                    field = value
                    notifyDataSetChanged()
                }
            }

        override fun getItemCount() = data.size

        override fun getItemId(position: Int): Long {
            return data[position].hashCode().toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return data.any { it.hashCode().toLong() == itemId }
        }

        override fun createFragment(position: Int): Fragment {
            return when (data[position]) {
                OnboardingPage.StoragePermission -> StoragePermissionFragment()
                OnboardingPage.MediaProviderSelector -> MediaProviderSelectionFragment.newInstance(isOnboarding)
                OnboardingPage.MusicDirectories -> DirectorySelectionFragment()
                OnboardingPage.Scanner -> MediaScannerFragment.newInstance(scanAutomatically = true, showRescanButton = false, dismissOnScanComplete = isOnboarding, showToolbar = true)
            }
        }
    }
}