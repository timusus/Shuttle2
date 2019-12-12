package com.simplecityapps.shuttle.ui.screens.onboarding

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.simplecityapps.put
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.onboarding.directories.DirectorySelectionFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider.MediaProviderSelectionFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.scanner.MediaScannerFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.storage.StoragePermissionFragment
import kotlinx.android.synthetic.main.fragment_onboarding.*
import me.relex.circleindicator.CircleIndicator3

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
    var uriMimeTypePairs: List<Pair<Uri, String>>?
}

interface OnboardingChild {
    val page: OnboardingPage
    fun getParent(): OnboardingParent
    fun handleNextButtonClick() {}
    fun handleBackButtonClick() {}
}

class OnboardingParentFragment : Fragment(), OnboardingParent {

    private lateinit var viewPager: ViewPager2

    private lateinit var adapter: OnboardingAdapter

    private var nextButton: Button? = null
    private var previousButton: Button? = null

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private var hasOnboarded: Boolean
        get() = preferences.getBoolean(PREF_HAS_ONBOARDED, false)
        set(value) = preferences.put(PREF_HAS_ONBOARDED, value)

    private var earlyExit = false


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasOnboarded && hasStoragePermission()) {
            earlyExit = true
            return
        }

        adapter = OnboardingAdapter(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (earlyExit) {
            exit()
            return
        }

        viewPager = view.findViewById(R.id.viewPager)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        val indicator: CircleIndicator3 = view.findViewById(R.id.indicator)
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
        nextButton?.setOnClickListener {
            val currentPage = adapter.data[viewPager.currentItem]
            childFragmentManager.fragments.filterIsInstance<OnboardingChild>().firstOrNull { it.page == currentPage }?.handleNextButtonClick()
        }

        previousButton = view.findViewById(R.id.previousButton)
        previousButton?.setOnClickListener {
            val currentPage = adapter.data[viewPager.currentItem]
            childFragmentManager.fragments.filterIsInstance<OnboardingChild>().firstOrNull { it.page == currentPage }?.handleBackButtonClick()
        }
    }

    override fun onDestroyView() {
        if (!earlyExit) {
            adapter.unregisterAdapterDataObserver(indicator.adapterDataObserver)
            viewPager.clearAdapterOnDetach()
            viewPager.adapter = null
        }
        earlyExit = false

        super.onDestroyView()
    }

    // PageCompletionListener Implementation

    override var uriMimeTypePairs: List<Pair<Uri, String>>? = null

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
        nextButton?.isVisible = false
    }

    override fun showNextButton(text: String?) {
        text?.let {
            nextButton?.text = text
        }
        nextButton?.isVisible = true
    }

    override fun toggleNextButton(enabled: Boolean) {
        nextButton?.isEnabled = enabled
    }

    override fun hideBackButton() {
        previousButton?.isVisible = false
    }

    override fun showBackButton(text: String?) {
        text?.let {
            previousButton?.text = text
        }
        previousButton?.isVisible = true
    }

    override fun exit() {
        earlyExit = true
        hasOnboarded = true
        findNavController().navigate(R.id.action_onboardingFragment_to_mainFragment)
    }


    // Private

    private fun hasStoragePermission(): Boolean {
        return (checkSelfPermission(context!!, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }


    // Static

    companion object {
        const val PREF_HAS_ONBOARDED = "has_onboarded"
        const val REQUEST_CODE_READ_STORAGE = 100
    }


    class OnboardingAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
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
                OnboardingPage.MediaProviderSelector -> MediaProviderSelectionFragment()
                OnboardingPage.MusicDirectories -> DirectorySelectionFragment()
                OnboardingPage.Scanner -> MediaScannerFragment()
            }
        }
    }
}