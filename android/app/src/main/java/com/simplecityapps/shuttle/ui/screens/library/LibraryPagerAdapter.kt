package com.simplecityapps.shuttle.ui.screens.library

import android.annotation.SuppressLint
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.LibraryTab
import com.simplecityapps.shuttle.ui.screens.library.albumartists.AlbumArtistListFragment
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListFragment
import com.simplecityapps.shuttle.ui.screens.library.genres.GenreListFragment
import com.simplecityapps.shuttle.ui.screens.library.playlists.PlaylistListFragment
import com.simplecityapps.shuttle.ui.screens.library.songs.SongListFragment

@SuppressLint("WrongConstant")
class LibraryPagerAdapter(
    val context: Context,
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    var items: List<LibraryTab> = emptyList()
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, items.size)
            }
        }

    fun getPageTitle(position: Int): CharSequence {
        return when (items[position]) {
            LibraryTab.Genres -> context.getString(R.string.genres)
            LibraryTab.Playlists -> context.getString(R.string.library_playlists)
            LibraryTab.Artists -> context.getString(R.string.artists)
            LibraryTab.Albums -> context.getString(R.string.albums)
            LibraryTab.Songs -> context.getString(R.string.songs)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (items[position]) {
            LibraryTab.Genres -> GenreListFragment.newInstance()
            LibraryTab.Playlists -> PlaylistListFragment.newInstance()
            LibraryTab.Artists -> AlbumArtistListFragment.newInstance()
            LibraryTab.Albums -> AlbumListFragment.newInstance()
            LibraryTab.Songs -> SongListFragment.newInstance()
        }
    }

    override fun containsItem(itemId: Long): Boolean {
        return items.map { it.ordinal.toLong() }.contains(itemId)
    }

    override fun getItemId(position: Int): Long {
        return items[position].ordinal.toLong()
    }
}
