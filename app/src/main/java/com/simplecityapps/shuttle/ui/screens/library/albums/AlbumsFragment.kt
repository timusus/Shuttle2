package com.simplecityapps.shuttle.ui.screens.library.albums

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.MainActivity
import com.simplecityapps.shuttle.R
import io.reactivex.disposables.CompositeDisposable

class AlbumsFragment : Fragment() {

    private val adapter = RecyclerAdapter()

    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view as RecyclerView

        view.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        // Todo: Inject Repository
        compositeDisposable.add(
            (activity as MainActivity).albumRepository.getAlbums().subscribe(
                { albums -> adapter.setData(albums.map { album -> AlbumBinder(album) }) },
                { error -> Log.e(MainActivity.TAG, error.toString()) })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    companion object {

        const val TAG = "AlbumsFragment"

        fun newInstance() = AlbumsFragment()
    }
}