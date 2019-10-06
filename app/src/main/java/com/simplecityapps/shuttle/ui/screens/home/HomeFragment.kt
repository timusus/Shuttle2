package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import com.simplecityapps.mediaprovider.repository.PlaylistQuery
import com.simplecityapps.mediaprovider.repository.PlaylistRepository
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.screens.library.playlists.detail.PlaylistDetailFragmentArgs
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.list_item_home_header.*
import timber.log.Timber
import javax.inject.Inject

class HomeFragment : Fragment(), Injectable {

    @Inject lateinit var presenter: HomePresenter

    @Inject lateinit var playlistRepository: PlaylistRepository


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyButton.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_historyFragment) }
        latestButton.setOnClickListener { findNavController().navigate(R.id.action_homeFragment_to_recentFragment) }
        favoritesButton.setOnClickListener {
            playlistRepository
                .getPlaylists(PlaylistQuery.PlaylistName("Favorites"))
                .first(emptyList())
                .map { playlists -> playlists.first() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { playlist -> findNavController().navigate(R.id.action_homeFragment_to_favoritesFragment, PlaylistDetailFragmentArgs(playlist).toBundle()) },
                    onError = { throwable -> Timber.e(throwable, "Failed to retrieve favorites playlist") }
                )
        }
        shuffleButton.setOnClickListener { presenter.shuffleAll() }
    }

    override fun onResume() {
        super.onResume()

        searchView.setOnSearchClickListener {
            if (isResumed)
                navigateToSearch()
        }
        searchView.setOnQueryTextFocusChangeListener { v, hasFocus ->
            if (hasFocus && isResumed) {
                navigateToSearch()
            }
        }
    }

    override fun onPause() {
        searchView.setOnSearchClickListener(null)
        searchView.setOnQueryTextFocusChangeListener(null)
        super.onPause()
    }

    private fun navigateToSearch() {
        if (findNavController().currentDestination?.id != R.id.searchFragment) {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment, null, null, FragmentNavigatorExtras(searchView to searchView.transitionName))
        }
    }
}