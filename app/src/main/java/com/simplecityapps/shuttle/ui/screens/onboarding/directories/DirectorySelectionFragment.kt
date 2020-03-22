package com.simplecityapps.shuttle.ui.screens.onboarding.directories

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import timber.log.Timber
import javax.inject.Inject


class DirectorySelectionFragment : Fragment(),
    Injectable,
    MusicDirectoriesContract.View,
    OnboardingChild {

    @Inject lateinit var presenter: MusicDirectoriesPresenter

    lateinit var adapter: RecyclerAdapter

    private var recyclerView: RecyclerView by autoCleared()

    private val preAnimationConstraints = ConstraintSet()
    private val postAnimationConstraints = ConstraintSet()
    private val transition = ChangeBounds()


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                presenter.handleSafResult(requireContext().contentResolver, intent)
            } ?: Timber.e("onActivityResult failed to handle result: Intent data null")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_directories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { getParent()?.goToPrevious() ?: Timber.e("Failed to navigate, parent is null") }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        presenter.bindView(this)
        presenter.loadData(requireContext().contentResolver)

        val addDirectoryButton: Button = view.findViewById(R.id.addDirectoryButton)
        addDirectoryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT)
            } else {
                presenter
            }
        }

        preAnimationConstraints.clone(view as ConstraintLayout)
        postAnimationConstraints.clone(view)
        postAnimationConstraints.clear(R.id.addDirectoryButton, ConstraintSet.TOP)

        transition.interpolator = FastOutSlowInInterpolator()
        transition.duration = 300
    }

    override fun onResume() {
        super.onResume()

        getParent()?.let { parent ->
            parent.showBackButton("Back")
            parent.showNextButton("Done")

            if (adapter.items.none { binder -> binder is DirectoryBinder } || adapter.items.any { binder -> binder is DirectoryBinder && !binder.data.traversalComplete }) {
                parent.toggleNextButton(enabled = false)
            } else {
                parent.toggleNextButton(enabled = true)
            }
        } ?: Timber.e("Failed to update back/done button - parent is null")
    }

    override fun onDestroyView() {
        adapter.dispose()
        presenter.unbindView()
        super.onDestroyView()
    }


    // MusicDirectoriesContract.View Implementation

    override fun setData(data: List<MusicDirectoriesContract.View.Data>) {
        getParent()?.let { parent ->
            parent.directories = data
                .filter { it.traversalComplete }
                .map { it.tree }

        } ?: Timber.e("Failed to set parent uri data - getParent() returned null")

        adapter.setData(data.map { DirectoryBinder(it, directoryBinderListener) }.toMutableList<ViewBinder>()) {
            getParent()?.let { parent ->
                if (adapter.items.none { binder -> binder is DirectoryBinder } || adapter.items.any { binder -> binder is DirectoryBinder && !binder.data.traversalComplete }) {
                    parent.toggleNextButton(enabled = false)
                } else {
                    parent.toggleNextButton(enabled = true)
                }

                (view as? ConstraintLayout)?.let { constraintLayout ->
                    TransitionManager.beginDelayedTransition(constraintLayout, transition)
                    if (adapter.items.isEmpty()) {
                        preAnimationConstraints.applyTo(constraintLayout)
                    } else {
                        postAnimationConstraints.applyTo(constraintLayout)
                    }
                }

            } ?: Timber.e("Failed to update update buttons, getParent() returned null")
        }
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

    // DirectoryBinder.Listener

    private val directoryBinderListener = object : DirectoryBinder.Listener {

        override fun onRemoveClicked(data: MusicDirectoriesContract.View.Data) {
            presenter.removeItem(data)
        }
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.MusicDirectories

    override fun getParent(): OnboardingParent? {
        return parentFragment as? OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent()?.goToNext() ?: Timber.e("Failed to goToNext() - getParent() returned null")
    }

    override fun handleBackButtonClick() {
        getParent()?.goToPrevious() ?: Timber.e("Failed to goToPrevious() - getParent() returned null")
    }


    // Static

    companion object {
        const val REQUEST_CODE_OPEN_DOCUMENT = 100
    }
}