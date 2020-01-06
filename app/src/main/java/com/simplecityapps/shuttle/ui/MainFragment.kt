package com.simplecityapps.shuttle.ui

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
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityapps.localmediaprovider.local.provider.mediastore.MediaStoreSongProvider
import com.simplecityapps.localmediaprovider.local.provider.taglib.TaglibSongProvider
import com.simplecityapps.mediaprovider.MediaImporter
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.persistence.PlaybackPreferenceManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.GeneralPreferenceManager
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.dagger.Injectable
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.taglib.FileScanner
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_main.*
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

class MainFragment
    : Fragment(),
    Injectable,
    QueueChangeCallback {

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var queueWatcher: QueueWatcher
    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var mediaImporter: MediaImporter

    @Inject lateinit var playbackPreferenceManager: PlaybackPreferenceManager
    @Inject lateinit var fileScanner: FileScanner

    @Inject lateinit var preferenceManager: GeneralPreferenceManager

    private val compositeDisposable = CompositeDisposable()

    private var onBackPressCallback: OnBackPressedCallback? = null

    // Todo: Use Presenter

    // Lifecycle

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController(activity!!, R.id.navHostFragment)

        val bottomNavigationView: BottomNavigationView = view.findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setupWithNavController(navController) { menuItem ->
            if (menuItem.itemId == R.id.bottomSheetFragment) {
                if (findNavController().currentDestination?.id != R.id.bottomSheetFragment) {
                    findNavController().navigate(R.id.action_mainFragment_to_bottomSheetFragment)
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
            multiSheetView.restoreSheet(savedInstanceState.getInt(STATE_CURRENT_SHEET))
        }

        // Update visible state of mini player
        queueWatcher.addCallback(this)

        multiSheetView.addSheetStateChangeListener(object : MultiSheetView.SheetStateChangeListener {

            override fun onSheetStateChanged(sheet: Int, state: Int) {
                updateBackPressListener()
            }

            override fun onSlide(sheet: Int, slideOffset: Float) {

            }
        })

        if (queueManager.getSize() == 0) {
            multiSheetView.hide(collapse = true, animate = false)
        }

        // Don't bother scanning for media again if we've already scanned once this session
        if (mediaImporter.scanCount < 1) {
            when (playbackPreferenceManager.songProvider) {
                PlaybackPreferenceManager.SongProvider.MediaStore -> {
                    mediaImporter.startScan(MediaStoreSongProvider(context!!.applicationContext))
                }
                PlaybackPreferenceManager.SongProvider.TagLib -> {
                    compositeDisposable.add(
                        Single.fromCallable {
                            context?.applicationContext?.contentResolver?.persistedUriPermissions
                                ?.filter { uriPermission -> uriPermission.isReadPermission }
                                ?.flatMap { uriPermission ->
                                    SafDirectoryHelper.buildFolderNodeTree(context!!.applicationContext.contentResolver, uriPermission.uri)?.getLeaves().orEmpty().map {
                                        it as SafDirectoryHelper.DocumentNode
                                    }
                                }.orEmpty()
                        }
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(
                                onSuccess = { nodes ->
                                    mediaImporter.startScan(TaglibSongProvider(context!!.applicationContext, fileScanner, nodes.map { Pair(it.uri, it.mimeType) }))
                                },
                                onError = { throwable -> Timber.e(throwable, "Failed to scan library") })
                    )
                }
            }
        }

        if (!preferenceManager.hasSeenChangelog && preferenceManager.showChangelogOnLaunch) {
            findNavController().navigate(R.id.action_mainFragment_to_changelogFragment)
        }
    }

    override fun onResume() {
        super.onResume()

        updateBackPressListener()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_SHEET, multiSheetView?.currentSheet ?: MultiSheetView.Sheet.NONE)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        queueWatcher.removeCallback(this)
        compositeDisposable.clear()
        super.onDestroyView()
    }

    // Private

    private fun updateBackPressListener() {
        onBackPressCallback?.remove()

        if (multiSheetView.currentSheet != MultiSheetView.Sheet.NONE) {
            // Todo: Remove activity dependency.
            onBackPressCallback = activity!!.onBackPressedDispatcher.addCallback {
                multiSheetView.consumeBackPress()
            }
        }
    }

    // QueueChangeCallback Implementation

    override fun onQueueChanged() {
        if (queueManager.getSize() == 0) {
            multiSheetView.hide(collapse = true, animate = false)
        } else {
            multiSheetView.unhide(true)
        }
    }


    // Static

    companion object {
        const val TAG = "MainFragment"
        const val STATE_CURRENT_SHEET = "current_sheet"
    }

}

fun BottomNavigationView.setupWithNavController(navController: NavController, onItemSelected: (MenuItem) -> (Unit)) {

    setOnNavigationItemSelectedListener { item ->
        val didNavigate = NavigationUI.onNavDestinationSelected(item, navController)
        onItemSelected(item)
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