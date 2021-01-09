package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.home.search.HeaderBinder
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingChild
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingPage
import com.simplecityapps.shuttle.ui.screens.onboarding.OnboardingParent
import com.simplecityapps.shuttle.ui.screens.onboarding.emby.EmbyConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.jellyfin.JellyfinConfigurationFragment
import com.simplecityapps.shuttle.ui.screens.onboarding.taglib.DirectorySelectionFragment
import timber.log.Timber
import javax.inject.Inject

class MediaProviderSelectionFragment :
    Fragment(),
    Injectable,
    OnboardingChild,
    MediaProviderSelectionContract.View,
    MediaProviderOptionsFragment.Listener {

    private var toolbar: Toolbar by autoCleared()

    private var isOnboarding = true

    private var recyclerView: RecyclerView by autoCleared()

    private var addProviderButton: Button by autoCleared()

    private lateinit var adapter: RecyclerAdapter

    @Inject lateinit var presenter: MediaProviderSelectionPresenter

    private val preAnimationConstraints = ConstraintSet()
    private val postAnimationConstraints = ConstraintSet()
    private val transition = ChangeBounds()


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isOnboarding = requireArguments().getBoolean(ARG_ONBOARDING)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_provider_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        toolbar = view.findViewById(R.id.toolbar)

        val subtitleLabel: TextView = view.findViewById(R.id.subtitleLabel)
        if (isOnboarding) {
            subtitleLabel.text = "Add media provider(s). You can change this later"
        } else {
            subtitleLabel.text = "Add media provider(s)"
        }

        addProviderButton = view.findViewById(R.id.addProviderButton)
        addProviderButton.setOnClickListener {
            presenter.addProviderClicked()
        }

        preAnimationConstraints.clone(view as ConstraintLayout)
        postAnimationConstraints.clone(view)
        postAnimationConstraints.clear(R.id.addProviderButton, ConstraintSet.TOP)

        transition.interpolator = FastOutSlowInInterpolator()
        transition.duration = 300

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        if (isOnboarding) {
            toolbar.title = "Discover your music"
            toolbar.navigationIcon = null
        } else {
            toolbar.title = "Media provider"
            toolbar.setNavigationOnClickListener {
                findNavController().popBackStack()
            }
        }

        // It seems we need some sort of arbitrary delay, to ensure the parent fragment has indeed finished its onViewCreated() and instantiated the next button.
        view?.postDelayed({
            getParent()?.let { parent ->
                parent.hideBackButton()
                parent.toggleNextButton(true)
                parent.showNextButton("Scan")
            } ?: Timber.e("Failed to update state - getParent() returned null")
        }, 50)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // MediaProviderSelectionContract.View Implementation

    override fun showMediaProviderSelectionDialog(mediaProviderTypes: List<MediaProvider.Type>) {
        MediaProviderOptionsFragment.newInstance(mediaProviderTypes).show(childFragmentManager)
    }

    override fun setMediaProviders(mediaProviderTypes: List<MediaProvider.Type>) {
        val viewBinders = mutableListOf<ViewBinder>()
        val localMediaTypes = mediaProviderTypes.filter { !it.isRemote }
        if (localMediaTypes.isNotEmpty()) {
            viewBinders.add(HeaderBinder("Local"))
            viewBinders.addAll(
                localMediaTypes.map { provider -> MediaProviderBinder(providerType = provider, listener = listener, showRemoveButton = true, showSubtitle = false) }
            )
        }
        val remoteMediaTypes = mediaProviderTypes.filter { it.isRemote }
        if (remoteMediaTypes.isNotEmpty()) {
            viewBinders.add(HeaderBinder("Remote"))
            viewBinders.addAll(
                remoteMediaTypes.map { provider -> MediaProviderBinder(providerType = provider, listener = listener, showRemoveButton = true, showSubtitle = false) }
            )
        }

        adapter.update(viewBinders)

        addProviderButton.isVisible = mediaProviderTypes.size != MediaProvider.Type.values().size

        (view as? ConstraintLayout)?.let { constraintLayout ->
            TransitionManager.beginDelayedTransition(constraintLayout, transition)
            if (mediaProviderTypes.isEmpty()) {
                preAnimationConstraints.applyTo(constraintLayout)
            } else {
                postAnimationConstraints.applyTo(constraintLayout)
            }
        }
    }


    // MediaProviderSelectionFragment.Listener Implementation

    override fun onMediaProviderSelected(providerType: MediaProvider.Type) {
        when (providerType) {
            MediaProvider.Type.MediaStore -> {
                presenter.addMediaProviderType(providerType)
            }
            MediaProvider.Type.Shuttle -> {
                presenter.addMediaProviderType(providerType)
                DirectorySelectionFragment.newInstance().show(childFragmentManager)
            }
            MediaProvider.Type.Emby -> {
                presenter.addMediaProviderType(providerType)
                EmbyConfigurationFragment.newInstance().show(childFragmentManager)
            }
            MediaProvider.Type.Jellyfin -> {
                presenter.addMediaProviderType(providerType)
                JellyfinConfigurationFragment.newInstance().show(childFragmentManager)
            }
        }
    }


    // MediaProviderViewBinder.Listener Implementation

    val listener = object : MediaProviderBinder.Listener {
        override fun onOverflowClicked(view: View, providerType: MediaProvider.Type) {
            val popupMenu = PopupMenu(requireContext(), view)
            popupMenu.inflate(R.menu.menu_media_provider_popup)
            popupMenu.menu.findItem(R.id.configure).isVisible = providerType != MediaProvider.Type.MediaStore
            popupMenu.setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.configure -> {
                        when (providerType) {
                            MediaProvider.Type.Shuttle -> DirectorySelectionFragment.newInstance().show(childFragmentManager)
                            MediaProvider.Type.Emby -> EmbyConfigurationFragment.newInstance().show(childFragmentManager)
                            MediaProvider.Type.Jellyfin -> JellyfinConfigurationFragment.newInstance().show(childFragmentManager)
                            MediaProvider.Type.MediaStore -> {
                                // Nothing to do
                            }
                        }
                    }
                    R.id.remove -> {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Remove ${providerType.title()}")
                            .setMessage("This will remove all songs, playlists & play counts associated with ${providerType.title()} media provder")
                            .setPositiveButton("Remove") { _, _ -> presenter.removeMediaProviderType(providerType) }
                            .setNegativeButton("Cancel", null)
                            .show()
                        return@setOnMenuItemClickListener true
                    }
                }
                true
            }
            popupMenu.show()
        }
    }


    // OnboardingChild Implementation

    override val page = OnboardingPage.MediaProviderSelector

    override fun getParent(): OnboardingParent? {
        return parentFragment as? OnboardingParent
    }

    override fun handleNextButtonClick() {
        getParent()?.goToNext() ?: Timber.e("Failed to goToNext() - getParent() returned null")
    }


    // Static

    companion object {
        const val ARG_ONBOARDING = "is_onboarding"
        fun newInstance(isOnboarding: Boolean = true): MediaProviderSelectionFragment {
            return MediaProviderSelectionFragment().withArgs { putBoolean(ARG_ONBOARDING, isOnboarding) }
        }
    }
}