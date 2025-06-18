package com.simplecityapps.shuttle.ui.screens.main

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.common.autoClearedNullable
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.screens.changelog.ChangelogDialogFragment
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.shuttle.ui.screens.trial.ThankYouDialogFragment
import com.simplecityapps.shuttle.ui.screens.trial.TrialDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import java.lang.ref.WeakReference
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MainFragment :
    Fragment(),
    MainContract.View {
    private var multiSheetView: MultiSheetView? by autoClearedNullable()

    private var onBackPressCallback: OnBackPressedCallback? = null

    @Inject
    lateinit var presenter: MainPresenter

    @Inject
    lateinit var generalPreferenceManager: GeneralPreferenceManager

    private lateinit var reviewManager: ReviewManager

    // Lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_main, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        multiSheetView = view.findViewById(R.id.multiSheetView)

        val navController = Navigation.findNavController(requireActivity(), R.id.navHostFragment)
        if (navController.currentDestination == null) {
            initializeNavGraph(navController)
        }

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
            val currentSheet = savedInstanceState.getInt(STATE_CURRENT_SHEET)
            val bottomNavTranslation = savedInstanceState.getFloat(STATE_BOTTOM_NAV_TRANSLATION_Y, 0f)
            multiSheetView?.restoreSheet(currentSheet)
            multiSheetView?.restoreBottomSheetTranslation(bottomNavTranslation)
        }

        multiSheetView?.addSheetStateChangeListener(
            object : MultiSheetView.SheetStateChangeListener {
                override fun onSheetStateChanged(
                    sheet: Int,
                    state: Int
                ) {
                    updateBackPressListener()
                }

                override fun onSlide(
                    sheet: Int,
                    slideOffset: Float
                ) {
                }
            }
        )

        reviewManager = ReviewManagerFactory.create(requireContext())

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

    override fun showTrialDialog() {
        TrialDialogFragment.newInstance().show(childFragmentManager)
    }

    override fun showThankYouDialog() {
        ThankYouDialogFragment.newInstance().show(childFragmentManager)
    }

    override fun launchReviewFlow() {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                reviewManager.launchReviewFlow(requireActivity(), reviewInfo)
            } else {
                // There was some problem, log or handle the error code.
                Timber.e(task.exception ?: Exception("Unknown"), "Failed to launch review flow")
            }
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun showCrashReportingDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pref_crash_reporting_title)
            .setMessage(R.string.crash_reporting_nag_text)
            .setPositiveButton(getString(R.string.dialog_button_enable)) { _, _ ->
                presenter.onCrashReportingToggled(true)
                Toast.makeText(requireContext(), getString(R.string.toast_crash_reporting_enabled), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_button_close, null)
            .show()
    }

    // Private

    private fun updateBackPressListener() {
        onBackPressCallback?.remove()

        if (multiSheetView?.currentSheet != MultiSheetView.Sheet.NONE) {
            // Todo: Remove activity dependency.
            activity?.let { activity ->
                onBackPressCallback =
                    activity.onBackPressedDispatcher.addCallback {
                        multiSheetView?.consumeBackPress()
                    }
            }
        }
    }

    private fun initializeNavGraph(navController: NavController) {
        val navGraph = navController.navInflater.inflate(R.navigation.main)

        if (generalPreferenceManager.showHomeOnLaunch) {
            navGraph.setStartDestination(R.id.homeFragment)
        } else {
            navGraph.setStartDestination(R.id.libraryFragment)
        }

        navController.graph = navGraph
    }

    // Static

    companion object {
        const val TAG = "MainFragment"
        const val STATE_CURRENT_SHEET = "current_sheet"
        const val STATE_BOTTOM_NAV_TRANSLATION_Y = "bottom_nav_alpha"
    }
}

fun BottomNavigationView.setupWithNavController(
    navController: NavController,
    onItemSelected: (MenuItem) -> (Unit)
) {
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

    fun matchDestination(
        destination: NavDestination,
        @IdRes destId: Int
    ): Boolean {
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
        }
    )
}
