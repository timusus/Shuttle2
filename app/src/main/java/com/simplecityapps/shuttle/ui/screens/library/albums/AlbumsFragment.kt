package com.simplecityapps.shuttle.ui.screens.library.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.FragmentNavigatorExtras
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.Regex
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import timber.log.Timber
import javax.inject.Inject

class AlbumsFragment : Fragment(), Injectable, AlbumBinder.Listener {

    private val adapter = SectionedAdapter()

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var albumRepository: AlbumRepository

    @Inject lateinit var imageLoader: ArtworkImageLoader


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable.add(
            albumRepository.getAlbums()
                .map { albums -> albums.sortedBy { album -> Regex.articlePattern.matcher(album.name).replaceAll("") } }
                .subscribe(
                { albums ->
                    adapter.setData(albums.map { album ->
                        val albumBinder = AlbumBinder(album, imageLoader)
                        albumBinder.listener = this
                        albumBinder
                    })
                },
                { error -> Timber.e(error, "Failed to retrieve albums") })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    // AlbumBinder.Listener Implementation

    override fun onAlbumClicked(album: Album, viewHolder: AlbumBinder.ViewHolder) {

        view?.findNavController()?.navigate(
            R.id.action_libraryFragment_to_albumDetailFragment,
            AlbumDetailFragmentArgs(album.id).toBundle(),
            null,
            FragmentNavigatorExtras(viewHolder.imageView to viewHolder.imageView.transitionName)
        )
    }


    // Static

    companion object {

        const val TAG = "AlbumsFragment"

        fun newInstance() = AlbumsFragment()
    }
}