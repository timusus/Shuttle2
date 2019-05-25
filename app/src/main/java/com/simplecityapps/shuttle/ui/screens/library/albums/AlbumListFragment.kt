package com.simplecityapps.shuttle.ui.screens.library.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject

class AlbumListFragment :
    Fragment(),
    Injectable,
    AlbumBinder.Listener,
    AlbumListContract.View {

    private val adapter = SectionedAdapter()

    @Inject lateinit var presenter: AlbumListPresenter

    @Inject lateinit var imageLoader: ArtworkImageLoader


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbums()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }

    // AlbumListContract.View Implementation

    override fun setAlbums(albums: List<Album>) {
        adapter.setData(albums.map { album ->
            AlbumBinder(album, imageLoader, this)
        })
    }

    // AlbumBinder.Listener Implementation

    override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {
        findNavController().navigate(
            R.id.action_libraryFragment_to_albumDetailFragment,
            AlbumDetailFragmentArgs(album.id).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }


    // Static

    companion object {

        const val TAG = "AlbumListFragment"

        fun newInstance() = AlbumListFragment()
    }
}