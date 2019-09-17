package com.simplecityapps.shuttle.ui.screens.onboarding.directories

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.recyclerview.clearAdapterOnDetach
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

    private lateinit var recyclerView: RecyclerView

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
                presenter.handleSafResult(context!!.contentResolver, intent)
            } ?: Timber.e("onActivityResult failed to handle result: Intent data null")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_onboarding_directories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        presenter.bindView(this)
        presenter.loadData(context!!.contentResolver)

        val addDirectoryButton: Button = view.findViewById(R.id.addDirectoryButton)
        addDirectoryButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_CODE_OPEN_DOCUMENT)
        }

        preAnimationConstraints.clone(view as ConstraintLayout)
        postAnimationConstraints.clone(view as ConstraintLayout)
        postAnimationConstraints.clear(R.id.addDirectoryButton, ConstraintSet.TOP)

        transition.interpolator = FastOutSlowInInterpolator()
        transition.duration = 300
    }

    override fun onResume() {
        super.onResume()

        getParent().showBackButton("Back")
        getParent().showNextButton("Done")

        if (adapter.items.none { binder -> binder is DirectoryBinder && binder.data.traversalComplete }) {
            getParent().toggleNextButton(false)
        } else {
            getParent().toggleNextButton(true)
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        recyclerView.clearAdapterOnDetach()
        super.onDestroyView()
    }


    // MusicDirectoriesContract.View Implementation

    override fun setData(data: List<MusicDirectoriesContract.View.Data>) {
        val constraintLayout = view as ConstraintLayout

        adapter.setData(data.map { data -> DirectoryBinder(data, directoryBinderListener) }.toMutableList<ViewBinder>()) {
            val adapterData = adapter.items.filterIsInstance<DirectoryBinder>().map { binder -> binder.data }

            if (adapter.items.none { binder -> binder is DirectoryBinder && binder.data.traversalComplete }) {
                getParent().toggleNextButton(false)
            } else {
                getParent().toggleNextButton(true)
            }

            TransitionManager.beginDelayedTransition(constraintLayout, transition)
            if (adapter.items.isEmpty()) {
                preAnimationConstraints.applyTo(constraintLayout)
            } else {
                postAnimationConstraints.applyTo(constraintLayout)
            }

            getParent().uriMimeTypePairs = adapterData
                .filter { it.traversalComplete }
                .flatMap {
                    it.tree.getLeaves()
                        .map { documentNode ->
                            documentNode as SafDirectoryHelper.DocumentNode
                            Pair(documentNode.uri, documentNode.mimeType)
                        }
                }
        }
    }


    // DirectoryBinder.Listener

    private val directoryBinderListener = object : DirectoryBinder.Listener {

        override fun onRemoveClicked(data: MusicDirectoriesContract.View.Data) {
            presenter.removeItem(data)
        }
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.MusicDirectories

    override fun getParent(): OnboardingParent {
        return parentFragment as OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent().goToNext()
    }

    override fun handleBackButtonClick() {
        getParent().goToPrevious()
    }


    // Static

    companion object {
        const val REQUEST_CODE_OPEN_DOCUMENT = 100
    }
}