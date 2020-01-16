package com.simplecityapps.shuttle.ui.screens.library.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.view.ToolbarHost

class FolderFragment : Fragment(), Injectable, ToolbarHost {

    var toolbar: Toolbar by autoCleared()
        @JvmName("getToolbar_") get // Resolves clash with ToolbarHost function


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)
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
