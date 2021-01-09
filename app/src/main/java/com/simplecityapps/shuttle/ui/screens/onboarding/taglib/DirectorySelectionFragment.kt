package com.simplecityapps.shuttle.ui.screens.onboarding.taglib

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import timber.log.Timber
import javax.inject.Inject


class DirectorySelectionFragment : DialogFragment(),
    Injectable,
    DirectorySelectionContract.View {

    @Inject lateinit var presenter: DirectorySelectionPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    private var recyclerView: RecyclerView by autoCleared()

    private var emptyLabel: TextView by autoCleared()


    // Lifecycle

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                presenter.handleSafResult(requireContext().contentResolver, intent)
            } ?: Timber.e("onActivityResult failed to handle result: Intent data null")
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.fragment_onboarding_directories, null)

        emptyLabel = view.findViewById(R.id.emptyLabel)

        adapter = RecyclerAdapter(lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        presenter.bindView(this)
        presenter.loadData(requireContext().contentResolver)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Media Directories")
            .setView(view)
            .setNeutralButton("Add Directory", null)
            .setPositiveButton("Done", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                presenter.presentDocumentProvider()
            }
        }

        return dialog
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // MusicDirectoriesContract.View Implementation

    override fun setData(data: List<DirectorySelectionContract.Directory>) {
        emptyLabel.isVisible = data.isEmpty()
        recyclerView.isVisible = data.isNotEmpty()
        adapter.update(data.map { DirectoryBinder(it, directoryBinderListener) }.toMutableList())
    }

    override fun startActivity(intent: Intent, requestCode: Int) {
        startActivityForResult(intent, requestCode)
    }

    override fun showDocumentProviderNotAvailable() {
        AlertDialog.Builder(requireContext())
            .setTitle("Missing Document Provider")
            .setMessage("A 'Document Provider' (file manager) app can't be found on your device. You may have to install one, or revert to using the 'basic' media scanner.")
            .setNeutralButton("Close", null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, "DirectorySelectionFragment")
    }


    // DirectoryBinder.Listener

    private val directoryBinderListener = object : DirectoryBinder.Listener {

        override fun onRemoveClicked(directory: DirectorySelectionContract.Directory) {
            presenter.removeItem(directory)
        }
    }


    // Static

    companion object {
        const val REQUEST_CODE_OPEN_DOCUMENT = 100
        fun newInstance() = DirectorySelectionFragment()
    }

}