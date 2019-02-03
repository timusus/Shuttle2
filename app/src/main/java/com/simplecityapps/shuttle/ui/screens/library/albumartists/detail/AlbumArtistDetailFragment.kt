package com.simplecityapps.shuttle.ui.screens.library.albumartists.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.simplecityapps.mediaprovider.model.Album
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumBinder
import com.simplecityapps.shuttle.ui.screens.library.albums.detail.AlbumDetailFragmentArgs
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_album_artist_detail.*
import javax.inject.Inject

class AlbumArtistDetailFragment :
    Fragment(),
    Injectable,
    AlbumArtistDetailContract.View,
    AlbumBinder.Listener {

    @Inject lateinit var presenterFactory: AlbumArtistDetailPresenter.Factory

    private lateinit var presenter: AlbumArtistDetailPresenter

    private val adapter = SectionedAdapter()


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        val albumArtistId = AlbumArtistDetailFragmentArgs.fromBundle(arguments!!).albumArtistId

        presenter = presenterFactory.create(albumArtistId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artist_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadData()
    }

    override fun onDestroyView() {
        adapter.dispose()

        presenter.unbindView()

        super.onDestroyView()
    }


    // AlbumArtistDetailContract.View Implementation

    override fun setData(albums: List<Album>) {
        adapter.setData(albums.map { album ->
            val albumBinder = AlbumBinder(album)
            albumBinder.listener = this
            albumBinder
        })
    }

    override fun setTitle(title: String) {
        toolbar?.title = title
    }


    // AlbumArtistBinder.Listener Implementation

    override fun onAlbumClicked(album: Album) {
        view?.findNavController()?.navigate(
            R.id.action_albumArtistDetailFragment_to_albumDetailFragment,
            AlbumDetailFragmentArgs.Builder(album.id).build().toBundle()
        )
    }
}