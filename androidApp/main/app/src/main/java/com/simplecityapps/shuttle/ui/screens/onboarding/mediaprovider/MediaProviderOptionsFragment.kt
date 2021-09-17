package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.adapter.ViewBinder
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.screens.home.search.HeaderBinder
import java.io.Serializable

class MediaProviderOptionsFragment : DialogFragment() {

    interface Listener {
        fun onMediaProviderSelected(providerType: MediaProviderType)
    }

    // Lifecycle

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.fragment_media_provider_options, null)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        val adapter = RecyclerAdapter(lifecycleScope)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(SpacesItemDecoration(8))

        val providerTypes = requireArguments().getSerializable(ARG_PROVIDER_TYPES) as List<MediaProviderType>

        val viewBinders = mutableListOf<ViewBinder>()
        val localMediaTypes = providerTypes.filter { !it.remote }
        if (localMediaTypes.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.media_provider_type_local)))
            viewBinders.addAll(
                localMediaTypes.map { provider -> MediaProviderBinder(providerType = provider, listener = listener, showRemoveButton = false, showSubtitle = true) }
            )
        }
        val remoteMediaTypes = providerTypes.filter { it.remote }
        if (remoteMediaTypes.isNotEmpty()) {
            viewBinders.add(HeaderBinder(getString(R.string.media_provider_type_remote)))
            viewBinders.addAll(
                remoteMediaTypes.map { provider -> MediaProviderBinder(providerType = provider, listener = listener, showRemoveButton = false, showSubtitle = true) }
            )
        }
        adapter.update(viewBinders)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.media_provider_add))
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .create()
    }


    // Public

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, "MediaProviderOptionsFragment")
    }


    // Private

    private val listener = object : MediaProviderBinder.Listener {
        override fun onItemClicked(providerType: MediaProviderType) {
            (parentFragment as? Listener)?.onMediaProviderSelected(providerType)
            dismiss()
        }
    }


    // Static

    companion object {
        const val ARG_PROVIDER_TYPES = "provider_types"
        fun newInstance(providerTypes: List<MediaProviderType>) = MediaProviderOptionsFragment().withArgs {
            putSerializable(ARG_PROVIDER_TYPES, providerTypes as Serializable)
        }
    }
}