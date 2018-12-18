package com.simplecityapps.shuttle.ui.screens.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumsFragment
import com.simplecityapps.shuttle.ui.screens.library.artists.AlbumArtistsFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongsFragment
import kotlinx.android.synthetic.main.fragment_library.*

class LibraryFragment : Fragment() {


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout.setupWithViewPager(viewPager, true)

        val adapter = PagerAdapter(childFragmentManager)
        adapter.addFragment("Artists", AlbumArtistsFragment.newInstance())
        adapter.addFragment("Albums", AlbumsFragment.newInstance())
        adapter.addFragment("Songs", SongsFragment.newInstance())
        adapter.notifyDataSetChanged()

        viewPager.adapter = adapter
    }

    companion object {

        const val TAG = "LibraryFragment"

        fun newInstance() = LibraryFragment()
    }
}


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