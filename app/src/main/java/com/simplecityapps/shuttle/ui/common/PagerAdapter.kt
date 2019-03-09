package com.simplecityapps.shuttle.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

class PagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

    private val data = linkedMapOf<String, Fragment>()

    fun addFragment(title: String, fragment: Fragment) {
        data[title] = fragment
    }

    override fun getItem(position: Int): Fragment {
        return data.values.elementAt(position)
    }

    override fun getCount(): Int {
        return data.size
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return data.keys.elementAt(position)
    }

}