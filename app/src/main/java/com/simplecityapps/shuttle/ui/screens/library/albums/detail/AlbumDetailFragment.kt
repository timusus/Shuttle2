package com.simplecityapps.shuttle.ui.screens.library.albums.detail

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_album_detail.*
import javax.inject.Inject

class AlbumDetailFragment :
    Fragment(),
    Injectable,
    AlbumDetailContract.View {

    @Inject lateinit var presenterFactory: AlbumDetailPresenter.Factory

    @Inject lateinit var imageLoader: ArtworkImageLoader

    private lateinit var presenter: AlbumDetailPresenter

    private val adapter = object : SectionedAdapter() {
        override fun getSectionName(viewBinder: ViewBinder?): String {
            return (viewBinder as? SongBinder)?.song?.track.toString()
        }
    }


    // Lifecycle

    override fun onAttach(context: Context) {
        super.onAttach(context)

        AndroidSupportInjection.inject(this)

        presenter = presenterFactory.create(AlbumDetailFragmentArgs.fromBundle(arguments!!).albumId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_detail, container, false)
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


    // AlbumDetailContract.View Implementation

    override fun setData(songs: List<Song>) {
        val discSongsMap = songs.groupBy { song -> song.disc }.toSortedMap()
        adapter.setData(discSongsMap.flatMap { entry ->
            val viewBinders = mutableListOf<ViewBinder>()
            if (discSongsMap.size > 1) {
                viewBinders.add(DiscNumberBinder(entry.key))
            }
            viewBinders.addAll(entry.value.map { song -> SongBinder(song, imageLoader) })
            viewBinders
        })
    }

    override fun setTitle(title: String, subtitle: String) {
        toolbar?.title = title
        toolbar?.subtitle = subtitle
    }
}