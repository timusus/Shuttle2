package com.simplecityapps.shuttle.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.simplecityapps.mediaprovider.repository.AlbumArtistRepository
import com.simplecityapps.mediaprovider.repository.AlbumRepository
import com.simplecityapps.mediaprovider.repository.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.playback.queue.QueueChangeCallback
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.playback.queue.QueueWatcher
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.screens.playback.PlaybackFragment
import com.simplecityapps.shuttle.ui.screens.playback.mini.MiniPlaybackFragment
import com.simplecityapps.shuttle.ui.screens.queue.QueueFragment
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity(), HasSupportFragmentInjector, QueueChangeCallback {

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    @Inject lateinit var queueManager: QueueManager
    @Inject lateinit var queueWatcher: QueueWatcher
    @Inject lateinit var playbackManager: PlaybackManager

    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var albumsRepository: AlbumRepository
    @Inject lateinit var albumArtistsRepository: AlbumArtistRepository


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.bottomNavigationView)
        navView.setupWithNavController(findNavController(R.id.navHostFragment))

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
        }

        // Update visible state of mini player
        queueWatcher.addCallback(this)

        onBackPressedDispatcher.addCallback {
             multiSheetView.consumeBackPress()
        }

        if (queueManager.getSize() == 0) {
            multiSheetView.hide(true, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        queueWatcher.removeCallback(this)
        compositeDisposable.clear()
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
    }
}
