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
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.PagerAdapter
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.dialog.EditTextAlertDialog
import com.simplecityapps.shuttle.ui.common.recyclerview.enforceSingleScrollDirection
import com.simplecityapps.shuttle.ui.common.recyclerview.recyclerView
import com.simplecityapps.shuttle.ui.common.view.CircularProgressView
import com.simplecityapps.shuttle.ui.common.view.ToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListFragment
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListFragment
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
class LibraryFragment : Fragment(),
    ToolbarHost,
    EditTextAlertDialog.Listener {

    private var viewPager: ViewPager2? = null

    private var tabLayout: TabLayout by autoCleared()

    private var _toolbar: Toolbar? by autoClearedNullable()
    private var _contextualToolbar: Toolbar? by autoClearedNullable()

    private var contextualToolbarHelper: ContextualToolbarHelper<*> by autoCleared()

    private var tabLayoutMediator: TabLayoutMediator? = null

    private var adapter: PagerAdapter? = null

    @Inject
    lateinit var preferenceManager: GeneralPreferenceManager

    @Inject
    lateinit var trialManager: TrialManager

    @Inject
    lateinit var promoCodeService: PromoCodeService


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

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        _toolbar = view.findViewById(R.id.toolbar)
        _contextualToolbar = view.findViewById(R.id.contextualToolbar)

        (requireActivity() as AppCompatActivity).setSupportActionBar(_toolbar!!)

        contextualToolbarHelper = ContextualToolbarHelper<Any>()
        contextualToolbarHelper.toolbar = toolbar
        contextualToolbarHelper.contextualToolbar = contextualToolbar

        adapter = PagerAdapter(
            fragmentManager = childFragmentManager,
            lifecycle = lifecycle,
            size = 5,
            fragmentFactory = { position ->
                when (position) {
                    0 -> GenreListFragment.newInstance()
                    1 -> PlaylistListFragment.newInstance()
                    2 -> AlbumArtistListFragment.newInstance()
                    3 -> AlbumListFragment.newInstance()
                    4 -> SongListFragment.newInstance()
                    else -> throw IllegalArgumentException()
                }
            },
            titleFactory = { position ->
                when (position) {
                    0 -> getString(R.string.genres)
                    1 -> getString(R.string.library_playlists)
                    2 -> getString(R.string.artists)
                    3 -> getString(R.string.albums)
                    4 -> getString(R.string.songs)
                    else -> throw IllegalArgumentException()
                }
            })

        viewPager?.let { viewPager ->
            viewPager.recyclerView.enforceSingleScrollDirection()
            viewPager.adapter = adapter
            if (savedInstanceState == null) {
                var tabIndex = preferenceManager.libraryTabIndex
                if (tabIndex == -1) {
                    tabIndex = 2
                }
                tabIndex.coerceAtMost(adapter!!.size - 1)
                viewPager.setCurrentItem(tabIndex, false)
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
        trialMenuItem.actionView.setOnClickListener {
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
                val daysRemainingText: TextView = trialMenuItem.actionView.findViewById(R.id.daysRemaining)
                daysRemainingText.text = TimeUnit.MILLISECONDS.toDays(trialState.timeRemaining).toString()
                val progress: CircularProgressView = trialMenuItem.actionView.findViewById(R.id.progress)
                progress.setProgress((trialState.timeRemaining / trialManager.trialLength.toDouble()).toFloat())
            }
            is TrialState.Expired -> {
                trialMenuItem.isVisible = true
                val daysRemainingText: TextView = trialMenuItem.actionView.findViewById(R.id.daysRemaining)
                daysRemainingText.text = String.format("%.1fx", trialState.multiplier())
                val progress: CircularProgressView = trialMenuItem.actionView.findViewById(R.id.progress)
                progress.setProgress(0f)
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
            preferenceManager.libraryTabIndex = position
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

