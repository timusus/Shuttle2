package com.simplecityapps.shuttle.ui.screens.home.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import au.com.simplecityapps.shuttle.imageloading.glide.GlideImageLoader
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.screens.library.songs.SongBinder
import kotlinx.android.synthetic.main.fragment_history.*
import javax.inject.Inject

class HistoryFragment : Fragment(), Injectable, HistoryContract.View, SongBinder.Listener {

    @Inject lateinit var presenter: HistoryPresenter

    private lateinit var imageLoader: ArtworkImageLoader

    private val adapter = RecyclerAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageLoader = GlideImageLoader(this)

        recyclerView.adapter = adapter

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        presenter.loadHistory()
    }

    override fun onDestroyView() {
        presenter.unbindView()

        super.onDestroyView()
    }


    // HistoryContract.View Implementation

    override fun setData(songs: List<Song>) {
        adapter.setData(songs.map { song -> SongBinder(song, imageLoader, this) })
    }


    // SongBinder.Listener Implementation
    override fun onSongClicked(song: Song) {

    }
}