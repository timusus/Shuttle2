package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject

class AlbumArtistListFragment :
    Fragment(),
    Injectable,
    AlbumArtistBinder.Listener,
    AlbumArtistListContract.View {

    private val adapter = SectionedAdapter()

    private lateinit var imageLoader: ArtworkImageLoader

    @Inject lateinit var presenter: AlbumArtistListPresenter

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadAlbumArtists()
    }

    override fun onDestroyView() {
        presenter.unbindView()
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }

    // AlbumArtistListContact.View Implementation

    override fun setAlbumArtists(albumArtists: List<AlbumArtist>) {
        adapter.setData(albumArtists.map { albumArtist ->
            AlbumArtistBinder(albumArtist, imageLoader, this)
        })
    }

    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumArtistClicked(albumArtist: AlbumArtist, viewHolder: AlbumArtistBinder.ViewHolder) {
        findNavController().navigate(
            R.id.action_libraryFragment_to_albumArtistDetailFragment,
            AlbumArtistDetailFragmentArgs(albumArtist.id).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }


    // Static

    companion object {

        const val TAG = "AlbumArtistListFragment"

        fun newInstance() = AlbumArtistListFragment()
    }
}