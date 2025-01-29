package com.simplecityapps.shuttle.ui.screens.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.NavigationRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.error.userDescription
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BottomDrawerSettingsFragment :
    BottomSheetDialogFragment(),
    BottomDrawerSettingsContract.View {
    // Lifecycle

    @Inject lateinit var presenter: BottomDrawerSettingsPresenter

    private var adapter: RecyclerAdapter by autoCleared()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_bottom_drawer, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter = RecyclerAdapter(viewLifecycleOwner.lifecycleScope)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        setSelectedItem(findNavController().currentDestination?.id)

        findNavController().addOnDestinationChangedListener(destinationChangedListener)

        presenter.loadData()
    }

    override fun onPause() {
        findNavController().removeOnDestinationChangedListener(destinationChangedListener)
        super.onPause()
    }

    override fun onDestroyView() {
        presenter.unbindView()

        super.onDestroyView()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    // Private

    private val destinationChangedListener = NavController.OnDestinationChangedListener { _, destination, _ -> setSelectedItem(destination.id) }

    private fun setSelectedItem(
        @NavigationRes destinationIdRes: Int?
    ) {
        presenter.currentDestinationIdRes = destinationIdRes
    }

    // SettingsViewBinder.Listener Implementation

    private val settingsItemClickListener =
        object : SettingsViewBinder.Listener {
            override fun onMenuItemClicked(settingsItem: SettingsMenuItem) {
                dismiss()

                when (settingsItem) {
                    SettingsMenuItem.Shuffle -> presenter.shuffleAll()
                    SettingsMenuItem.SleepTimer -> SleepTimerDialogFragment.newInstance().show(requireFragmentManager())
                    SettingsMenuItem.Dsp -> findNavController().navigate(R.id.action_bottomSheetFragment_to_equalizerFragment)
                    SettingsMenuItem.Settings -> findNavController().navigate(R.id.action_bottomSheetFragment_to_settingsFragment)
                }
            }
        }

    // BottomDrawerSettingsContract.View Implementation

    override fun setData(
        settingsItems: List<SettingsMenuItem>,
        currentDestination: Int?
    ) {
        adapter.update(settingsItems.map { settingsItem -> SettingsViewBinder(settingsItem, false, settingsItemClickListener) })
    }

    override fun showLoadError(error: Error) {
        Toast.makeText(context, error.userDescription(resources), Toast.LENGTH_LONG).show()
    }

    // Static

    companion object {
        const val TAG = "BottomDrawerSettingsFragment"

        fun newInstance() = BottomDrawerSettingsFragment()
    }
}
