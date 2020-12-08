package com.simplecityapps.shuttle.ui.screens.onboarding.mediaprovider

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.MediaProvider
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.recyclerview.SpacesItemDecoration
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import java.io.Serializable

class MediaProviderOptionsFragment : DialogFragment() {

    interface Listener {
        fun onMediaProviderSelected(providerType: MediaProvider.Type)
    }

    // Lifecycle

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.fragment_media_provider_options, null)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        val adapter = RecyclerAdapter(lifecycleScope)
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(SpacesItemDecoration(8))

        val providerTypes = requireArguments().getSerializable(ARG_PROVIDER_TYPES) as List<MediaProvider.Type>

        adapter.update(
            providerTypes.map { providerType ->
                MediaProviderBinder(providerType = providerType, listener = listener, showRemoveButton = false, showSubtitle = true)
            }
        )

        return AlertDialog.Builder(requireContext())
            .setTitle("Add Media Provider")
            .setView(view)
            .setNegativeButton("Close", null)
            .create()
    }


    // Public

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, "MediaProviderOptionsFragment")
    }


    // Private

    private val listener = object : MediaProviderBinder.Listener {
        override fun onItemClicked(providerType: MediaProvider.Type) {
            (parentFragment as? Listener)?.onMediaProviderSelected(providerType)
            dismiss()
        }
    }


    // Static

    companion object {
        const val ARG_PROVIDER_TYPES = "provider_types"
        fun newInstance(providerTypes: List<MediaProvider.Type>) = MediaProviderOptionsFragment().withArgs {
            putSerializable(ARG_PROVIDER_TYPES, providerTypes as Serializable)
        }
    }
}