package com.simplecityapps.shuttle.ui.screens.library

import android.os.Bundle
import android.os.Parcelable
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.simplecityapps.networking.retrofit.NetworkResult
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.recyclerview.enforceSingleScrollDirection
import com.simplecityapps.shuttle.ui.common.recyclerview.recyclerView
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.simplecityapps.shuttle.ui.common.view.CircularProgressView
import com.simplecityapps.shuttle.ui.common.view.ToolbarHost
import com.simplecityapps.shuttle.ui.screens.trial.PromoCodeDialogFragment
import com.simplecityapps.shuttle.ui.screens.trial.TrialDialogFragment
import com.simplecityapps.trial.PromoCodeService
import com.simplecityapps.trial.TrialManager
import com.simplecityapps.trial.TrialState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment :
    Fragment(),
    ToolbarHost,
    EditTextAlertDialog.Listener {

    private var viewPager: ViewPager2? = null

    private var tabLayout: TabLayout by autoCleared()

    private var _toolbar: Toolbar? by autoClearedNullable()
    private var _contextualToolbar: Toolbar? by autoClearedNullable()

    private var contextualToolbarHelper: ContextualToolbarHelper<*> by autoCleared()

    private var loadingView: CircularLoadingView by autoCleared()

    private var tabLayoutMediator: TabLayoutMediator? = null

    private var adapter: LibraryPagerAdapter? = null

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var trialManager: TrialManager

    @Inject
    lateinit var promoCodeService: PromoCodeService

    private lateinit var libraryTabs: List<LibraryTab>

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        libraryTabs = preferenceManager.allLibraryTabs.filter { preferenceManager.enabledLibraryTabs.contains(it) }

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        _toolbar = view.findViewById(R.id.toolbar)
        _contextualToolbar = view.findViewById(R.id.contextualToolbar)

        (requireActivity() as AppCompatActivity).setSupportActionBar(_toolbar!!)

        contextualToolbarHelper = ContextualToolbarHelper<Any>()
        contextualToolbarHelper.toolbar = toolbar
        contextualToolbarHelper.contextualToolbar = contextualToolbar

        adapter = LibraryPagerAdapter(requireContext(), childFragmentManager, lifecycle)
        adapter!!.items = libraryTabs

        loadingView = view.findViewById(R.id.loadingView)
        loadingView.setState(CircularLoadingView.State.None)
        if (libraryTabs.isEmpty()) {
            loadingView.setState(CircularLoadingView.State.Empty(getString(R.string.library_tabs_empty)))
        }

        viewPager?.let { viewPager ->
            viewPager.recyclerView.enforceSingleScrollDirection()
            viewPager.adapter = adapter
            if (savedInstanceState == null) {
                val currentLibraryTab = preferenceManager.currentLibraryTab ?: libraryTabs.getOrNull(libraryTabs.indexOf(LibraryTab.Artists)) ?: libraryTabs.firstOrNull()
                currentLibraryTab?.let {
                    viewPager.setCurrentItem(libraryTabs.indexOf(it), false)
                }
            }
            viewPager.registerOnPageChangeCallback(pageChangeListener)

            tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = adapter!!.getPageTitle(position)
            }
        }
        tabLayoutMediator?.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            trialManager.trialState.collect {
                this@LibraryFragment.requireActivity().invalidateOptionsMenu()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        inflater.inflate(R.menu.menu_library, menu)

        val trialMenuItem = menu.findItem(R.id.trial)
        trialMenuItem.actionView!!.setOnClickListener {
            TrialDialogFragment.newInstance().show(childFragmentManager)
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val trialMenuItem = menu.findItem(R.id.trial)
        when (val trialState = trialManager.trialState.value) {
            is TrialState.Unknown, is TrialState.Paid -> {
                trialMenuItem.isVisible = false
            }
            is TrialState.Trial -> {
                trialMenuItem.isVisible = true
                val daysRemainingText: TextView = trialMenuItem.actionView!!.findViewById(R.id.daysRemaining)
                daysRemainingText.text = TimeUnit.MILLISECONDS.toDays(trialState.timeRemaining).toString()
                val progress: CircularProgressView = trialMenuItem.actionView!!.findViewById(R.id.progress)
                progress.setProgress((trialState.timeRemaining / trialManager.trialLength.toDouble()).toFloat())
            }
            is TrialState.Expired -> {
                trialMenuItem.isVisible = true
                val daysRemainingText: TextView = trialMenuItem.actionView!!.findViewById(R.id.daysRemaining)
                daysRemainingText.text = String.format("%.1fx", trialState.multiplier())
                val progress: CircularProgressView = trialMenuItem.actionView!!.findViewById(R.id.progress)
                progress.setProgress(0f)
            }
            is TrialState.Pretrial -> {
                // Nothing to do
            }
        }
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        viewPager?.unregisterOnPageChangeCallback(pageChangeListener)
        viewPager?.adapter = null
        viewPager = null

        (requireActivity() as AppCompatActivity).setSupportActionBar(null)

        super.onDestroyView()
    }

    // ViewPager2.OnPageChangeCallback Implementation

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            contextualToolbarHelper.hide()
            preferenceManager.currentLibraryTab = libraryTabs[position]
        }
    }

    // EditTextAlertDialog.Listener Implementation

    override fun onSave(text: String?, extra: Parcelable?) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = promoCodeService.getPromoCode(text!!)) {
                is NetworkResult.Success -> {
                    PromoCodeDialogFragment.newInstance(result.body.promoCode).show(childFragmentManager)
                }
                is NetworkResult.Failure -> {
                    Toast.makeText(requireContext(), "Failed to retrieve promo code", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ToolbarHost Implementation

    override
    val toolbar: Toolbar?
        get() = _toolbar

    override
    val contextualToolbar: Toolbar?
        get() = _contextualToolbar
}
