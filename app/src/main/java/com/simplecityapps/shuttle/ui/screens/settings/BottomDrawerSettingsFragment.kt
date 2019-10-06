package com.simplecityapps.shuttle.ui.screens.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.NavigationRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.view.BottomSheetOverlayView
import com.simplecityapps.shuttle.ui.screens.sleeptimer.SleepTimerDialogFragment
import timber.log.Timber
import javax.inject.Inject

class BottomDrawerSettingsFragment :
    Fragment(),
    Injectable,
    BottomDrawerSettingsContract.View {

    // Lifecycle

    @Inject
    lateinit var presenter: BottomDrawerSettingsPresenter

    private lateinit var adapter: RecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        adapter = RecyclerAdapter()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bottom_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.adapter = adapter

        presenter.bindView(this)
        presenter.loadData()
    }

    override fun onResume() {
        super.onResume()

        setSelectedItem(findNavController().currentDestination?.id)
        findNavController().addOnDestinationChangedListener { _, destination, _ ->
            setSelectedItem(destination.id)
        }
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // Private

    private fun setSelectedItem(@NavigationRes destinationIdRes: Int?) {
        presenter.currentDestinationIdRes = destinationIdRes
    }


    // SettingsViewBinder.Listener Implementation

    private val settingsItemClickListener = object : SettingsViewBinder.Listener {

        override fun onMenuItemClicked(settingsItem: SettingsMenuItem) {
            settingsItem.navDestination?.let { navDestination ->
                findNavController().navigate(navDestination)
            }

            (view?.parent as? BottomSheetOverlayView)?.hide(true)

            when (settingsItem) {
                SettingsMenuItem.SleepTimer -> {
                    fragmentManager?.let { fragmentManager ->
                        SleepTimerDialogFragment().show(fragmentManager)
                    } ?: Timber.e("Failed to show sleep timer: parent fragment manager null")
                }
            }
        }
    }


    // BottomDrawerSettingsContract.View Implementation

    override fun setData(settingsItems: List<SettingsMenuItem>, currentDestination: Int?) {
        adapter.setData(settingsItems.map { settingsItem -> SettingsViewBinder(settingsItem, settingsItem.navDestination == currentDestination, settingsItemClickListener) })
    }
}