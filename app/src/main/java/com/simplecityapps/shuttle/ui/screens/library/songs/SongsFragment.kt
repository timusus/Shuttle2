package com.simplecityapps.shuttle.ui.screens.library.songs

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

class SongsFragment : Fragment() {

    private val adapter = RecyclerAdapter()

    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_songs, container, false)
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
            (activity as MainActivity).songsRepository.getSongs()
                .subscribe(
                { songs -> adapter.setData(songs.map { song -> SongBinder(song) }) },
                { error -> Log.e(MainActivity.TAG, error.toString()) })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    companion object {

        const val TAG = "SongsFragment"

        fun newInstance() = SongsFragment()
    }
}