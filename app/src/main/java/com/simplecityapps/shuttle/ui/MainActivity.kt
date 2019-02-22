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
import com.simplecityapps.shuttle.R
import dagger.android.DispatchingAndroidInjector
import dagger.android.support.HasSupportFragmentInjector
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.inject.Inject

class MainActivity : AppCompatActivity(), HasSupportFragmentInjector {

    private val compositeDisposable = CompositeDisposable()

    @Inject lateinit var dispatchingAndroidInjector: DispatchingAndroidInjector<Fragment>

    @Inject lateinit var songRepository: SongRepository
    @Inject lateinit var albumsRepository: AlbumRepository
    @Inject lateinit var albumArtistsRepository: AlbumArtistRepository


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navView.setupWithNavController(findNavController(R.id.navHostFragment))

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            onHasPermission()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        compositeDisposable.clear()
    }


    // Permissions

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        onHasPermission()
    }

    private fun onHasPermission() {
        compositeDisposable.add(songRepository.populate().subscribe({ Timber.i("Populate complete")}))
        compositeDisposable.add(albumsRepository.populate().subscribe())
        compositeDisposable.add(albumArtistsRepository.populate().subscribe())
    }


    // HasSupportFragmentInjector Implementation

    override fun supportFragmentInjector() = dispatchingAndroidInjector


    // Static

    companion object {
        const val TAG = "MainActivity"
    }
}
