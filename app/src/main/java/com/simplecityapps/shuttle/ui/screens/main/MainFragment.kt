package com.simplecityapps.shuttle.ui.screens.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

class MainFragment : Fragment(),
    Injectable,
    MainContract.View {

    private var multiSheetView: MultiSheetView? by autoClearedNullable()

    private var onBackPressCallback: OnBackPressedCallback? = null

    @Inject lateinit var presenter: MainPresenter


    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        multiSheetView = view.findViewById(R.id.multiSheetView)

        val navController = Navigation.findNavController(requireActivity(), R.id.navHostFragment)

        val bottomNavigationView: BottomNavigationView = view.findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setupWithNavController(navController) { menuItem ->
            if (menuItem.itemId == R.id.bottomSheetFragment) {
                if (findNavController().currentDestination?.id != menuItem.itemId) {
                    try {
                        findNavController().navigate(R.id.action_mainFragment_to_bottomSheetFragment)
                    } catch (e: IllegalArgumentException) {
                        Timber.e(e, "Failed to navigate to bottom sheet")
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.sheet1Container, PlaybackFragment(), "PlaybackFragment")
                .add(R.id.sheet1PeekView, MiniPlaybackFragment(), "MiniPlaybackFragment")
                .add(R.id.sheet2Container, QueueFragment.newInstance(), "QueueFragment")
                .commit()
        } else {
            multiSheetView?.restoreSheet(savedInstanceState.getInt(STATE_CURRENT_SHEET))
            multiSheetView?.restoreBottomSheetTranslation(savedInstanceState.getFloat(STATE_BOTTOM_NAV_TRANSLATION_Y, 0f))
        }

        multiSheetView?.addSheetStateChangeListener(object : MultiSheetView.SheetStateChangeListener {
            override fun onSheetStateChanged(sheet: Int, state: Int) {
                updateBackPressListener()
            }

            override fun onSlide(sheet: Int, slideOffset: Float) {

            }
        })

        presenter.bindView(this)
    }

    override fun onResume() {
        super.onResume()

        updateBackPressListener()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_SHEET, multiSheetView?.currentSheet ?: MultiSheetView.Sheet.NONE)
        multiSheetView?.bottomSheetTranslation?.let { translationY -> outState.putFloat(STATE_BOTTOM_NAV_TRANSLATION_Y, translationY) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        presenter.unbindView()
        super.onDestroyView()
    }


    // MainContract.View Implementation

    override fun toggleSheet(visible: Boolean) {
        if (visible) {
            multiSheetView?.unhide(true)
        } else {
            multiSheetView?.hide(collapse = true, animate = false)
        }
    }

    override fun showChangelog() {
        ChangelogDialogFragment.newInstance().show(childFragmentManager)
    }

    // Private

    private fun updateBackPressListener() {
        onBackPressCallback?.remove()

        if (multiSheetView?.currentSheet != MultiSheetView.Sheet.NONE) {
            // Todo: Remove activity dependency.
                activity?.let {activity->
                    onBackPressCallback = activity.onBackPressedDispatcher.addCallback {
                        multiSheetView?.consumeBackPress()
                    }
                }
        }
    }


    // Static

    companion object {
        const val TAG = "MainFragment"
        const val STATE_CURRENT_SHEET = "current_sheet"
        const val STATE_BOTTOM_NAV_TRANSLATION_Y = "bottom_nav_alpha"
    }
}


fun BottomNavigationView.setupWithNavController(navController: NavController, onItemSelected: (MenuItem) -> (Unit)) {

    setOnNavigationItemSelectedListener { item ->
        var didNavigate = false
        try {
            didNavigate = NavigationUI.onNavDestinationSelected(item, navController)
            onItemSelected(item)
        } catch (e: NullPointerException) {
            Timber.e(e, "Failed to setup bottom nav view")
        }
        didNavigate
    }

    fun matchDestination(destination: NavDestination, @IdRes destId: Int): Boolean {
        var currentDestination: NavDestination? = destination
        while (currentDestination!!.id != destId && currentDestination.parent != null) {
            currentDestination = currentDestination.parent
        }
        return currentDestination.id == destId
    }

    val weakReference = WeakReference(this)
    navController.addOnDestinationChangedListener(
        object : NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                val view = weakReference.get()
                if (view == null) {
                    navController.removeOnDestinationChangedListener(this)
                    return
                }
                val menu = view.menu
                var h = 0
                val size = menu.size()
                while (h < size) {
                    val item = menu.getItem(h)
                    if (matchDestination(destination, item.itemId)) {
                        item.isChecked = true
                    }
                    h++
                }
            }
        })
}