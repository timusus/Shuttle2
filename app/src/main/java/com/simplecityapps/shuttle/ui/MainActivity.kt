package com.simplecityapps.shuttle.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.view.multisheet.MultiSheetView
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import com.simplecityapps.shuttle.ui.screens.settings.BottomSheetSettingsManager
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.lang.ref.WeakReference
import javax.inject.Inject

class MainActivity :
    AppCompatActivity(),
    HasSupportFragmentInjector,
    QueueChangeCallback,
    BottomSheetSettingsManager {

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var queueWatcher: QueueWatcher
    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var albumsRepository: AlbumRepository
    @Inject lateinit var albumArtistsRepository: AlbumArtistRepository

    private var onBackPressCallback: OnBackPressedCallback? = null

    private lateinit var settingsBottomSheetBackground: View
    private var settingsBottomSheetBackgroundAnimation: ValueAnimator? = null
    private lateinit var settingsBottomSheetBehavior: BottomSheetBehavior<View>


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navController = findNavController(R.id.navHostFragment)
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottomNavigationView)

        settingsBottomSheetBackground = findViewById(R.id.bottomSheetBackground)
        settingsBottomSheetBackground.setOnClickListener { hideBottomSettingsSheet() }
        settingsBottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottomDrawerFragment))
        settingsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        bottomNavigationView.setupWithNavController(navController) { menuItem ->
            if (menuItem.itemId == R.id.navigation_menu) {
                showBottomSettingsSheet()
            } else {
                hideBottomSettingsSheet()
            }
        }

        settingsBottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, offset: Float) {
                if (settingsBottomSheetBackgroundAnimation?.isRunning != true) {
                    var alpha = 1f - (offset * -1f)
                    if (alpha.isNaN()) {
                        alpha = 1f
                    }
                    settingsBottomSheetBackground.alpha = alpha
                }
            }

            override fun onStateChanged(bottomSheet: View, state: Int) {
                if (state == BottomSheetBehavior.STATE_HIDDEN) {
                    settingsBottomSheetBackgroundAnimation?.cancel()
                    settingsBottomSheetBackgroundAnimation = settingsBottomSheetBackground.fadeOut()
                }
                updateBackPressListener()
            }
        })

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            onHasPermission()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
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
    }

    override fun onResume() {
        super.onResume()

        updateBackPressListener()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_CURRENT_SHEET, multiSheetView.currentSheet)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()

        queueWatcher.removeCallback(this)
        compositeDisposable.clear()
    }


    // Private

    override fun showBottomSettingsSheet() {
        settingsBottomSheetBackgroundAnimation?.cancel()
        settingsBottomSheetBackgroundAnimation = settingsBottomSheetBackground.fadeIn()
        settingsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun hideBottomSettingsSheet() {
        settingsBottomSheetBackgroundAnimation?.cancel()
        settingsBottomSheetBackgroundAnimation = settingsBottomSheetBackground.fadeOut()
        settingsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun updateBackPressListener() {
        onBackPressCallback?.remove()

        if (settingsBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            onBackPressCallback = onBackPressedDispatcher.addCallback {
                hideBottomSettingsSheet()
            }
            return
        }

        if (multiSheetView.currentSheet != MultiSheetView.Sheet.NONE) {
            onBackPressCallback = onBackPressedDispatcher.addCallback {
                multiSheetView.consumeBackPress()
            }
        }
    }


    // Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onHasPermission()
    }

    private fun onHasPermission() {
        compositeDisposable.add(songRepository.populate().subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to populate song repository") }))
        compositeDisposable.add(albumsRepository.populate().subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to populate album repository") }))
        compositeDisposable.add(albumArtistsRepository.populate().subscribeBy(onError = { throwable -> Timber.e(throwable, "Failed to populate artist repository") }))
    }


    // HasSupportFragmentInjector Implementation

    override fun supportFragmentInjector() = dispatchingAndroidInjector


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
        const val TAG = "MainActivity"
        const val STATE_CURRENT_SHEET = "current_sheet"
    }
}

fun View.fadeIn(): ValueAnimator? {
    if (isVisible && alpha == 1f) return null
    alpha = 0f
    isVisible = true
    val animator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 1f)
    animator.duration = 250
    animator.start()
    return animator
}

fun View.fadeOut(): ValueAnimator? {
    if (!isVisible) {
        return null
    }
    val animator = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
    animator.duration = 250
    animator.start()
    animator.addListener(onEnd = { isVisible = false })
    return animator
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