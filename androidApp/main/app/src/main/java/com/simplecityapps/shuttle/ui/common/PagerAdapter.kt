package com.simplecityapps.shuttle.ui.common

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

@SuppressLint("WrongConstant")
class PagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    val size: Int,
    val fragmentFactory: (position: Int) -> Fragment,
    val titleFactory: (position: Int) -> String
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    fun getPageTitle(position: Int): CharSequence {
        return titleFactory(position)
    }

    override fun getItemCount(): Int {
        return size
    }

    override fun createFragment(position: Int): Fragment {
        return fragmentFactory(position)
    }
}
