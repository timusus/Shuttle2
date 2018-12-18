package com.simplecityapps.shuttle.ui.screens.library.artists

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.MainActivity
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class AlbumArtistsFragment : Fragment(), Injectable {

    private val adapter = RecyclerAdapter()

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var albumArtistRepository: AlbumArtistRepository


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_album_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view as RecyclerView

        view.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable.add(
            albumArtistRepository.getAlbumArtists().subscribe(
                { albumArtists -> adapter.setData(albumArtists.map { albumArtist -> AlbumArtistBinder(albumArtist) }) },
                { error -> Log.e(MainActivity.TAG, error.toString()) })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    // Static

    companion object {

        const val TAG = "AlbumArtistFragment"

        fun newInstance() = AlbumArtistsFragment()
    }
}