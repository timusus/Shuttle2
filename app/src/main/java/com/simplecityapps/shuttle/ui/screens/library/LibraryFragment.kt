package com.simplecityapps.shuttle.ui.screens.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.ContextualToolbarHelper
import com.simplecityapps.shuttle.ui.common.PagerAdapter
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.common.view.ToolbarHost
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListFragment
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListFragment

class LibraryFragment : Fragment(), ToolbarHost {

    private var viewPager: ViewPager2? = null

    private var tabLayout: TabLayout by autoCleared()

    private var _toolbar: Toolbar? by autoClearedNullable()
    private var _contextualToolbar: Toolbar? by autoClearedNullable()

    private var contextualToolbarHelper: ContextualToolbarHelper<*> by autoCleared()

    private var tabLayoutMediator: TabLayoutMediator? = null


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        val adapter = PagerAdapter(
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
                    0 -> "Genres"
                    1 -> "Playlists"
                    2 -> "Artists"
                    3 -> "Albums"
                    4 -> "Songs"
                    else -> throw IllegalArgumentException()
                }
            })

        viewPager?.let { viewPager ->
            viewPager.adapter = adapter
            viewPager.offscreenPageLimit = 2
            viewPager.clearAdapterOnDetach()
            viewPager.setCurrentItem(2, false)
            viewPager.registerOnPageChangeCallback(pageChangeListener)

            tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = adapter.getPageTitle(position)
            }
        }
        tabLayoutMediator?.attach()

        _toolbar = view.findViewById(R.id.toolbar)
        _contextualToolbar = view.findViewById(R.id.contextualToolbar)

        contextualToolbarHelper = ContextualToolbarHelper<Any>()
        contextualToolbarHelper.toolbar = toolbar
        contextualToolbarHelper.contextualToolbar = contextualToolbar
    }

    override fun onDestroyView() {
        tabLayoutMediator?.detach()
        viewPager?.unregisterOnPageChangeCallback(pageChangeListener)
        viewPager = null
        super.onDestroyView()
    }


    // ViewPager2.OnPageChangeCallback Implementation

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            contextualToolbarHelper.hide()
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