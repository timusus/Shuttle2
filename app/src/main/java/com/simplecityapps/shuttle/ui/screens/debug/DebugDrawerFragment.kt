package com.simplecityapps.shuttle.ui.screens.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.dagger.NetworkingModule
import com.simplecityapps.shuttle.ui.common.PagerAdapter
import com.simplecityapps.shuttle.ui.screens.onboarding.scanner.MediaScannerFragment

class DebugDrawerFragment : Fragment(), Injectable {

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_debug_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabLayout: TabLayout = view.findViewById(R.id.tabLayout)
        val viewPager: ViewPager = view.findViewById(R.id.viewPager)
        tabLayout.setupWithViewPager(viewPager, true)

        val adapter = PagerAdapter(childFragmentManager)
        adapter.addFragment("Media Scanner", MediaScannerFragment.newInstance(scanAutomatically = false, showRescanButton = true, dismissOnScanComplete = false, showToolbar = false))
        adapter.addFragment("Debug", LoggingFragment.newInstance(LoggingFragment.Filter(excludesTag = NetworkingModule.NETWORK_LOG_TAG)))
        adapter.notifyDataSetChanged()

        viewPager.adapter = adapter
    }
}