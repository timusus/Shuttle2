package com.simplecityapps.shuttle.ui.screens.opensource

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LicensesDialogFragment : DialogFragment() {

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.fragment_dialog_licenses, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.addItemDecoration(SpacesItemDecoration(4))
        adapter = RecyclerAdapter(lifecycleScope)
        recyclerView.adapter = adapter

        val libraries = Libs(requireContext())
            .libraries
            .sortedBy { library -> library.libraryName }

        adapter.update(
            libraries.map { LibraryBinder(it, listener) }
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.open_source_dialog_licenses_title))
            .setView(view)
            .setNegativeButton(requireContext().getString(R.string.dialog_button_close), null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    private val listener = object : LibraryBinder.Listener {
        override fun onItemClick(library: Library) {
            if (library.libraryWebsite.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(library.libraryWebsite)
                startActivity(intent)
            }
        }
    }

    companion object {
        const val TAG = "LicensesDialogFragment"

        fun newInstance() = LicensesDialogFragment()
    }
}
