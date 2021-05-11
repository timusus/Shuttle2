package com.simplecityapps.shuttle.ui.screens.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.NetworkingModule
import com.simplecityapps.shuttle.ui.common.PagerAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DebugDrawerFragment : Fragment() {

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout: TabLayout = view.findViewById(R.id.tabLayout)
        val viewPager: ViewPager2 = view.findViewById(R.id.viewPager)

        val adapter = PagerAdapter(
            fragmentManager = childFragmentManager,
            lifecycle = lifecycle,
            size = 1,
            fragmentFactory = { position ->
                when (position) {
                    0 -> LoggingFragment.newInstance(LoggingFragment.Filter(excludesTag = NetworkingModule.NETWORK_LOG_TAG))
                    else -> throw IllegalArgumentException()
                }
            },
            titleFactory = { position ->
                when (position) {
                    0 -> "Debug"
                    else -> throw IllegalArgumentException()
                }
            })

        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getPageTitle(position)
        }.attach()
    }
}