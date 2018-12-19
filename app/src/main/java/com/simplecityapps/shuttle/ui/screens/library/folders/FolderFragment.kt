package com.simplecityapps.shuttle.ui.screens.library.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.view.ToolbarHost
import kotlinx.android.synthetic.main.fragment_folders.*

class FolderFragment : Fragment(), Injectable, ToolbarHost {


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(com.simplecityapps.shuttle.R.layout.fragment_folders, container, false)
    }


    // ToolbarHost Implementation

    override fun getToolbar(): Toolbar? {
        return toolbar
    }


    // Static

    companion object {

        const val TAG = "FolderFragment"

        fun newInstance() = FolderFragment()
    }
}
