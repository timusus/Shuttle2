package com.simplecityapps.shuttle.ui.screens.library.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.SectionedAdapter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_folder_detail.*
import timber.log.Timber
import javax.inject.Inject

class SongsFragment : Fragment(), Injectable {

    private val adapter = object : SectionedAdapter() {
        override fun getSectionName(viewBinder: ViewBinder?): String {
            return (viewBinder as? SongBinder)?.song?.albumArtistName?.firstOrNull()?.toString() ?: ""
        }
    }

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var songRepository: SongRepository


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        compositeDisposable.add(
            songRepository.getSongs().subscribe(
                { songs -> adapter.setData(songs.map { song -> SongBinder(song) }) },
                { error -> Timber.e(error, "Failed to retrieve songs") })
        )
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }


    // Static

    companion object {

        const val TAG = "SongsFragment"

        fun newInstance() = SongsFragment()
    }
}