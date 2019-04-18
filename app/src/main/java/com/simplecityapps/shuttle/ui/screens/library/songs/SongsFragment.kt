package com.simplecityapps.shuttle.ui.screens.library.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import au.com.simplecityapps.shuttle.imageloading.ArtworkImageLoader
import com.simplecityapps.adapter.RecyclerListener
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import javax.inject.Inject

class SongsFragment : Fragment(), Injectable, SongsContract.View {

    private val adapter = object : SectionedAdapter() {
        override fun getSectionName(viewBinder: ViewBinder?): String {
            return (viewBinder as? SongBinder)?.song?.albumArtistName?.firstOrNull()?.toString() ?: ""
        }
    }

    @Inject lateinit var presenter: SongsPresenter

    @Inject lateinit var imageLoader: ArtworkImageLoader


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
        recyclerView.setRecyclerListener(RecyclerListener())

        presenter.bindView(this)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // SongsContract.View Implementation

    override fun setData(songs: List<Song>) {
        adapter.setData(songs.map { song ->
            SongBinder(song, imageLoader, songBinderListener)
        })
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(), Toast.LENGTH_LONG).show()
    }


    // Private

    private val songBinderListener = object : SongBinder.Listener {

        override fun onSongClicked(song: Song) {
            presenter.onSongClicked(song)
        }
    }


    // Static

    companion object {

        const val TAG = "SongsFragment"

        fun newInstance() = SongsFragment()
    }
}