package com.simplecityapps.shuttle.ui.screens.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment.findNavController
import com.simplecityapps.shuttle.R
import kotlinx.android.synthetic.main.list_item_home_header.*

class HomeFragment : Fragment() {

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyButton.setOnClickListener { findNavController(this).navigate(R.id.action_homeFragment_to_historyFragment) }
        latestButton.setOnClickListener { findNavController(this).navigate(R.id.action_homeFragment_to_recentFragment) }
    }
}