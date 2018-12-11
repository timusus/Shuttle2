package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.view.HomeButton
import kotlinx.android.synthetic.main.list_item_home_header.*

class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyButton.setType(HomeButton.Type.History)
        latestButton.setType(HomeButton.Type.Latest)
        favoritesButton.setType(HomeButton.Type.Favorites)
        shuffleButton.setType(HomeButton.Type.Shuffle)
    }

    companion object {

        const val TAG = "HomeFragment"

        fun newInstance() = HomeFragment()
    }

}

interface HomeContract {

    interface Presenter {

    }

    interface View {

    }

}