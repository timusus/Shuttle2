package com.simplecityapps.shuttle.ui.screens.library.albumartists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.mediaprovider.model.AlbumArtist
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.library.albumartists.detail.AlbumArtistDetailFragmentArgs
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import timber.log.Timber
import javax.inject.Inject

class AlbumArtistsFragment : Fragment(), Injectable, AlbumArtistBinder.Listener {

    private val adapter = SectionedAdapter()

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var albumArtistRepository: AlbumArtistRepository

    @Inject lateinit var imageLoader: ArtworkImageLoader


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable.add(
            albumArtistRepository.getAlbumArtists().subscribe(
                { albumArtists ->
                    adapter.setData(albumArtists.map { albumArtist ->
                        val albumArtistBinder = AlbumArtistBinder(albumArtist, imageLoader)
                        albumArtistBinder.listener = this
                        albumArtistBinder
                    })
                },
                { error -> Timber.e(error, "Failed to retrieve album artists") })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumArtistClicked(albumArtist: AlbumArtist) {

        view?.findNavController()?.navigate(
            R.id.action_libraryFragment_to_albumArtistDetailFragment,
            AlbumArtistDetailFragmentArgs.Builder(albumArtist.id).build().toBundle()
        )
    }


    // Static

    companion object {

        const val TAG = "AlbumArtistsFragment"

        fun newInstance() = AlbumArtistsFragment()
    }
}